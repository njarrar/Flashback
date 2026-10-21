"""Gets the App Store version for the app in this repo ready and sends it for review,
through the App Store Connect API.

The version and build number come from the Xcode project. If App Store Connect has no
version by that number yet, the script renames a version that was never released (one
being prepared, or one Apple sent back), or makes a new one after a release. A version
still waiting for review is pulled back first only when REPLACE is "true". It then picks
the uploaded build with that number, the "What's New" text from app-store/listing, and
the App Review contact and notes.

    python submit.py

Settings come from the environment:
    ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8   the App Store Connect API key (see app-store/README.md)
    REVIEW_PHONE                            the phone number App Review can call
    REVIEW_EMAIL, REVIEW_NAME               who App Review contacts

The App Review notes come from app-store/review-notes.txt.
    SUBMIT                                  "true" sends the version for review
    REPLACE                                 "true" pulls back a version waiting for review
    ATTACHMENT                              a file (such as a screen recording) to attach to the App
                                            Review details, in place of any attached before

The store text, screenshots and build must already be on the version. Apple says what is
still missing when the submission fails.
"""
import hashlib
import os
import pathlib
import re
import sys
import time

import jwt
import requests

BUNDLE_ID = "com.njarrar.backthen"
API = "https://api.appstoreconnect.apple.com"
ROOT = pathlib.Path(__file__).parents[2]
NOTES = (ROOT / "app-store" / "review-notes.txt").read_text(encoding="utf-8").strip()
PROJECT = (ROOT / "ios" / "BackThen.xcodeproj" / "project.pbxproj").read_text(encoding="utf-8")
VERSION = re.search(r"MARKETING_VERSION = ([0-9.]+);", PROJECT).group(1)
BUILD = re.search(r"CURRENT_PROJECT_VERSION = ([0-9]+);", PROJECT).group(1)
LISTING = ROOT / "app-store" / "listing"
# Versions Apple has released, or approved for release.
RELEASED = {"READY_FOR_SALE", "READY_FOR_DISTRIBUTION", "PROCESSING_FOR_DISTRIBUTION", "PENDING_DEVELOPER_RELEASE",
            "PENDING_APPLE_RELEASE", "ACCEPTED", "REPLACED_WITH_NEW_VERSION", "REMOVED_FROM_SALE",
            "DEVELOPER_REMOVED_FROM_SALE"}
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
WAITING = {"WAITING_FOR_REVIEW", "IN_REVIEW"}


def token():
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        os.environ["ASC_KEY_P8"],
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


def call(method, path, body=None, soft=False, **params):
    r = requests.request(method, API + path, json=body, params=params,
                         headers={"Authorization": f"Bearer {token()}"}, timeout=60)
    if r.status_code >= 400:
        for e in r.json().get("errors", []):
            print(f"  {e.get('title')}: {e.get('detail')}")
            # Apple lists what blocks a review under associatedErrors.
            for errors in (e.get("meta") or {}).get("associatedErrors", {}).values():
                for a in errors:
                    print(f"    - {a.get('title')}: {a.get('detail')}")
        if soft:
            print(f"  {method} {path} answered {r.status_code}")
            return None
        sys.exit(f"{method} {path} failed with {r.status_code}")
    return r.json() if r.content else {}


def check(app, version):
    """Prints every store field, per language, and says whether all of them are filled in."""
    ok = True

    def show(label, value):
        nonlocal ok
        filled = value not in (None, "", [], 0)
        ok &= filled
        text = str(value).replace("\n", " ")
        print(f"  {'ok  ' if filled else 'MISSING'} {label}: {text[:70] + '...' if len(text) > 70 else text}")

    v = version["attributes"]
    print("Version:")
    show("version", v.get("versionString"))
    show("copyright", v.get("copyright"))
    show("release", v.get("releaseType"))
    build = call("GET", f"/v1/appStoreVersions/{version['id']}/build").get("data")
    show("build", build and build["attributes"]["version"])

    info = [i for i in call("GET", f"/v1/apps/{app['id']}/appInfos")["data"]
            if i["attributes"].get("state", i["attributes"].get("appStoreState")) != "READY_FOR_DISTRIBUTION"][0]
    print("App information:")
    for rel in ("primaryCategory", "secondaryCategory"):
        show(rel, (call("GET", f"/v1/appInfos/{info['id']}/{rel}").get("data") or {}).get("id"))
    show("content rights", app["attributes"].get("contentRightsDeclaration"))
    for loc in call("GET", f"/v1/appInfos/{info['id']}/appInfoLocalizations")["data"]:
        a = loc["attributes"]
        print(f"App information, {a['locale']}:")
        show("name", a.get("name"))
        show("subtitle", a.get("subtitle"))
        show("privacy policy URL", a.get("privacyPolicyUrl"))

    for loc in call("GET", f"/v1/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]:
        a = loc["attributes"]
        print(f"Store page, {a['locale']}:")
        show("description", a.get("description"))
        show("keywords", a.get("keywords"))
        show("promotional text", a.get("promotionalText"))
        show("support URL", a.get("supportUrl"))
        sets = call("GET", f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets")["data"]
        for s in sets:
            shots = call("GET", f"/v1/appScreenshotSets/{s['id']}/appScreenshots")["data"]
            show(f"screenshots {s['attributes']['screenshotDisplayType']}", len(shots))
        if not sets:
            show("screenshots", 0)
    return ok


def state(version):
    a = version["attributes"]
    return a.get("appVersionState") or a.get("appStoreState")


def versions(app):
    return call("GET", f"/v1/apps/{app['id']}/appStoreVersions", **{"filter[platform]": "IOS", "limit": 50})["data"]


def pull_back(app, version):
    """Cancels the review of a version still waiting for one, so it can be changed."""
    for s in call("GET", "/v1/reviewSubmissions", **{
            "filter[app]": app["id"], "filter[platform]": "IOS",
            "filter[state]": "WAITING_FOR_REVIEW,IN_REVIEW"})["data"]:
        call("PATCH", f"/v1/reviewSubmissions/{s['id']}", {"data": {
            "type": "reviewSubmissions", "id": s["id"], "attributes": {"canceled": True}}})
    for _ in range(30):
        version = call("GET", f"/v1/appStoreVersions/{version['id']}")["data"]
        if state(version) in EDITABLE:
            return version
        time.sleep(10)
    sys.exit(f"Version {version['attributes']['versionString']} is still {state(version)}.")


def target_version(app):
    """The version numbered as in the Xcode project, renamed or made if need be."""
    all_versions = versions(app)
    for v in all_versions:
        if v["attributes"]["versionString"] == VERSION:
            print(f"Version {VERSION} is {state(v)}.")
            return v
    open_ones = [v for v in all_versions if state(v) not in RELEASED]
    if open_ones:
        v = open_ones[0]
        old = v["attributes"]["versionString"]
        print(f"Version {old} was never released and is {state(v)}.")
        if state(v) in WAITING:
            if os.environ.get("REPLACE") != "true":
                sys.exit(f"Version {old} is waiting for review. Run again with replace on to send {VERSION} in its place.")
            v = pull_back(app, v)
            print(f"Pulled version {old} back from review.")
        elif state(v) not in EDITABLE:
            sys.exit(f"Version {old} can't be changed while it is {state(v)}.")
        # Same record, new number: Apple's messages about it and its store page stay.
        v = call("PATCH", f"/v1/appStoreVersions/{v['id']}", {"data": {
            "type": "appStoreVersions", "id": v["id"], "attributes": {"versionString": VERSION}}})["data"]
        print(f"Renamed version {old} to {VERSION}.")
        return v
    v = call("POST", "/v1/appStoreVersions", {"data": {
        "type": "appStoreVersions", "attributes": {"platform": "IOS", "versionString": VERSION, "releaseType": "AFTER_APPROVAL"},
        "relationships": {"app": {"data": {"type": "apps", "id": app["id"]}}}}})["data"]
    print(f"Made version {VERSION}.")
    return v


def attach_build(app, version):
    """Picks the uploaded build with the project's build number, once Apple has processed it."""
    for _ in range(60):
        builds = call("GET", "/v1/builds", **{
            "filter[app]": app["id"], "filter[version]": BUILD,
            "filter[preReleaseVersion.version]": VERSION, "filter[preReleaseVersion.platform]": "IOS"})["data"]
        if builds and builds[0]["attributes"].get("processingState") == "VALID":
            call("PATCH", f"/v1/appStoreVersions/{version['id']}/relationships/build",
                 {"data": {"type": "builds", "id": builds[0]["id"]}})
            print(f"Build {BUILD} picked.")
            return
        print(f"Waiting for build {VERSION} ({BUILD}): {builds[0]['attributes'].get('processingState') if builds else 'not uploaded yet'}")
        time.sleep(30)
    sys.exit(f"Build {VERSION} ({BUILD}) never showed up. Run the App Store workflow first.")


def whats_new(app, version):
    """Sets "What's New" from the listing files. The first release has none."""
    if not any(state(v) in RELEASED for v in versions(app)):
        return
    for loc in call("GET", f"/v1/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]:
        notes = LISTING / loc["attributes"]["locale"] / "release-notes.txt"
        if notes.exists():
            call("PATCH", f"/v1/appStoreVersionLocalizations/{loc['id']}", {"data": {
                "type": "appStoreVersionLocalizations", "id": loc["id"],
                "attributes": {"whatsNew": notes.read_text(encoding="utf-8").strip()}}})
    print("What's New set.")


def attach(detail, path):
    """Attaches a file to the App Review details, in place of any attached before."""
    for old in call("GET", f"/v1/appStoreReviewDetails/{detail['id']}/appStoreReviewAttachments")["data"]:
        call("DELETE", f"/v1/appStoreReviewAttachments/{old['id']}")
    data = path.read_bytes()
    a = call("POST", "/v1/appStoreReviewAttachments", {"data": {
        "type": "appStoreReviewAttachments", "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appStoreReviewDetail": {"data": {"type": "appStoreReviewDetails", "id": detail["id"]}}}}})["data"]
    for op in a["attributes"]["uploadOperations"]:
        part = data[op["offset"]:op["offset"] + op["length"]]
        headers = {h["name"]: h["value"] for h in op.get("requestHeaders") or []}
        requests.request(op["method"], op["url"], data=part, headers=headers, timeout=300).raise_for_status()
    call("PATCH", f"/v1/appStoreReviewAttachments/{a['id']}", {"data": {
        "type": "appStoreReviewAttachments", "id": a["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()}}})
    for _ in range(40):
        done = (call("GET", f"/v1/appStoreReviewAttachments/{a['id']}")["data"]["attributes"]
                .get("assetDeliveryState") or {}).get("state")
        if done == "COMPLETE":
            print(f"Attached {path.name} for App Review.")
            return
        if done == "FAILED":
            sys.exit(f"Apple could not take {path.name}.")
        time.sleep(15)
    sys.exit(f"Apple is still processing {path.name}.")


def main():
    app = call("GET", "/v1/apps", **{"filter[bundleId]": BUNDLE_ID})["data"][0]
    print(f"App: {app['attributes']['name']}, project version {VERSION} ({BUILD})")

    version = target_version(app)
    if state(version) not in EDITABLE:
        sys.exit(f"Version {VERSION} is {state(version)}, so there is nothing to send.")
    attach_build(app, version)
    whats_new(app, version)

    name = os.environ.get("REVIEW_NAME", "").split(" ", 1)
    details = {
        "contactFirstName": name[0],
        "contactLastName": name[1] if len(name) > 1 else "",
        "contactEmail": os.environ["REVIEW_EMAIL"],
        "contactPhone": os.environ["REVIEW_PHONE"],
        "demoAccountRequired": False,
        "notes": NOTES,
    }
    existing = call("GET", f"/v1/appStoreVersions/{version['id']}/appStoreReviewDetail").get("data")
    if existing:
        call("PATCH", f"/v1/appStoreReviewDetails/{existing['id']}",
             {"data": {"type": "appStoreReviewDetails", "id": existing["id"], "attributes": details}})
    else:
        existing = call("POST", "/v1/appStoreReviewDetails", {"data": {
            "type": "appStoreReviewDetails", "attributes": details,
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})["data"]
    print("App Review contact and notes set.")
    if os.environ.get("ATTACHMENT"):
        attach(existing, pathlib.Path(os.environ["ATTACHMENT"]))

    if not check(app, version):
        sys.exit("Fill in the fields above first. Nothing was sent for review.")

    if os.environ.get("SUBMIT") != "true":
        print("Not sending for review (SUBMIT is off).")
        return

    # A submission that already holds this version (one Apple sent back) is sent again as it
    # is, so Apple's messages stay with it. Apple won't add the version to a second one.
    holding = empty = None
    for sub in call("GET", "/v1/reviewSubmissions", **{
            "filter[app]": app["id"], "filter[platform]": "IOS", "limit": 50})["data"]:
        st = sub["attributes"].get("state")
        its = call("GET", f"/v1/reviewSubmissions/{sub['id']}/items", include="appStoreVersion")["data"]
        mine = any(((i["relationships"].get("appStoreVersion") or {}).get("data") or {}).get("id") == version["id"]
                   for i in its)
        print(f"Submission {sub['id']}: {st}, items {[i['attributes'].get('state') for i in its]}"
              f"{', holds this version' if mine else ''}")
        if mine and st not in ("COMPLETE", "COMPLETING", "CANCELING"):
            holding = (sub, its)
        elif not its and st == "READY_FOR_REVIEW":
            empty = sub
    if holding:
        submission, items = holding
    else:
        submission = empty or call("POST", "/v1/reviewSubmissions", {"data": {
            "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
            "relationships": {"app": {"data": {"type": "apps", "id": app["id"]}}}}})["data"]
        call("POST", "/v1/reviewSubmissionItems", {"data": {
            "type": "reviewSubmissionItems",
            "relationships": {
                "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission["id"]}},
                "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})
        items = []
    # Apple won't take a sent-back submission again until its items are marked resolved.
    for item in items:
        if item["attributes"].get("state") not in (None, "READY_FOR_REVIEW", "ACCEPTED", "APPROVED"):
            call("PATCH", f"/v1/reviewSubmissionItems/{item['id']}", {"data": {
                "type": "reviewSubmissionItems", "id": item["id"], "attributes": {"resolved": True}}}, soft=True)
    call("PATCH", f"/v1/reviewSubmissions/{submission['id']}", {"data": {
        "type": "reviewSubmissions", "id": submission["id"], "attributes": {"submitted": True}}})
    print(f"Version {version['attributes']['versionString']} sent for App Review.")


if __name__ == "__main__":
    main()
