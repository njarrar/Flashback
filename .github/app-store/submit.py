"""Fills in the App Review details for the version being prepared and sends it for review,
through the App Store Connect API.

    python submit.py

Settings come from the environment:
    ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8   the App Store Connect API key (see app-store/README.md)
    REVIEW_PHONE                            the phone number App Review can call
    REVIEW_EMAIL, REVIEW_NAME               who App Review contacts

The App Review notes come from app-store/review-notes.txt.
    SUBMIT                                  "true" sends the version for review

The store text, screenshots and build must already be on the version. Apple says what is
still missing when the submission fails.
"""
import os
import pathlib
import sys
import time

import jwt
import requests

BUNDLE_ID = "com.njarrar.backthen"
API = "https://api.appstoreconnect.apple.com"
NOTES = (pathlib.Path(__file__).parents[2] / "app-store" / "review-notes.txt").read_text(encoding="utf-8").strip()


def token():
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"},
        os.environ["ASC_KEY_P8"],
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


def call(method, path, body=None, **params):
    r = requests.request(method, API + path, json=body, params=params,
                         headers={"Authorization": f"Bearer {token()}"}, timeout=60)
    if r.status_code >= 400:
        for e in r.json().get("errors", []):
            print(f"  {e.get('title')}: {e.get('detail')}")
            # Apple lists what blocks a review under associatedErrors.
            for errors in (e.get("meta") or {}).get("associatedErrors", {}).values():
                for a in errors:
                    print(f"    - {a.get('title')}: {a.get('detail')}")
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


def main():
    app = call("GET", "/v1/apps", **{"filter[bundleId]": BUNDLE_ID})["data"][0]
    print(f"App: {app['attributes']['name']}")

    versions = call("GET", f"/v1/apps/{app['id']}/appStoreVersions", **{"filter[platform]": "IOS"})["data"]
    editable = [v for v in versions if v["attributes"].get("appVersionState", v["attributes"].get("appStoreState"))
                in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")]
    if not editable:
        sys.exit("No version waiting to be sent for review.")
    version = editable[0]
    print(f"Version: {version['attributes']['versionString']}")

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
        call("POST", "/v1/appStoreReviewDetails", {"data": {
            "type": "appStoreReviewDetails", "attributes": details,
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})
    print("App Review contact and notes set.")

    if not check(app, version):
        sys.exit("Fill in the fields above first. Nothing was sent for review.")

    if os.environ.get("SUBMIT") != "true":
        print("Not sending for review (SUBMIT is off).")
        return

    open_ones = call("GET", "/v1/reviewSubmissions", **{
        "filter[app]": app["id"], "filter[platform]": "IOS", "filter[state]": "READY_FOR_REVIEW"})["data"]
    if open_ones:
        submission = open_ones[0]
    else:
        submission = call("POST", "/v1/reviewSubmissions", {"data": {
            "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
            "relationships": {"app": {"data": {"type": "apps", "id": app["id"]}}}}})["data"]
    items = call("GET", f"/v1/reviewSubmissions/{submission['id']}/items")["data"]
    if not items:
        call("POST", "/v1/reviewSubmissionItems", {"data": {
            "type": "reviewSubmissionItems",
            "relationships": {
                "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission["id"]}},
                "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})
    call("PATCH", f"/v1/reviewSubmissions/{submission['id']}", {"data": {
        "type": "reviewSubmissions", "id": submission["id"], "attributes": {"submitted": True}}})
    print(f"Version {version['attributes']['versionString']} sent for App Review.")


if __name__ == "__main__":
    main()
