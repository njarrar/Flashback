"""Sends the app and its store listing to Google Play through the Play Developer API.

    python publish.py <bundle.aab> <play-store dir>

Settings come from the environment:
    PLAY_SERVICE_ACCOUNT_JSON  the service account key (see play-store/README.md)
    TRACK                      Play track for the bundle, "alpha" is the first closed test
    SEND_BUNDLE, SEND_LISTING  "true" or "false"

The bundle goes to TRACK with listing/<locale>/release-notes.txt as its notes. The listing
sends title, short and full description per locale, plus the icon, feature graphic and
phone screenshots. Everything goes in one edit, so Play gets all of it or none of it.
"""
import json
import os
import pathlib
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.njarrar.backthen"


def read(path):
    return path.read_text(encoding="utf-8").strip()


def send(api, aab, store, track, send_bundle, send_listing, status):
    edits = api.edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    ids = dict(packageName=PACKAGE, editId=edit)
    locales = sorted(p.name for p in (store / "listing").iterdir() if p.is_dir())

    if send_bundle:
        media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
        code = edits.bundles().upload(**ids, media_body=media).execute()["versionCode"]
        print(f"Uploaded bundle, version code {code}")
        notes = [{"language": l, "text": read(store / "listing" / l / "release-notes.txt")}
                 for l in locales if (store / "listing" / l / "release-notes.txt").exists()]
        release = {"versionCodes": [str(code)], "status": status, "releaseNotes": notes}
        edits.tracks().update(**ids, track=track, body={"track": track, "releases": [release]}).execute()
        print(f"Set {track} track to version code {code} ({status})")

    if send_listing:
        for l in locales:
            d = store / "listing" / l
            edits.listings().update(**ids, language=l, body={
                "language": l,
                "title": read(d / "title.txt"),
                "shortDescription": read(d / "short-description.txt"),
                "fullDescription": read(d / "full-description.txt"),
            }).execute()
            lang = l.split("-")[0]
            images = [("icon", store / "graphics/icon-512.png"),
                      ("featureGraphic", store / "graphics" / lang / "feature-graphic.png")]
            shots = sorted((store / "screenshots" / lang).glob("*.png"))
            if shots:
                edits.images().deleteall(**ids, language=l, imageType="phoneScreenshots").execute()
                images += [("phoneScreenshots", s) for s in shots]
            for kind, path in images:
                if path.exists():
                    media = MediaFileUpload(str(path), mimetype="image/png")
                    edits.images().upload(**ids, language=l, imageType=kind, media_body=media).execute()
            print(f"Updated {l} listing with {len(shots)} screenshots")

    try:
        edits.commit(**ids).execute()
    except HttpError as e:
        if "changesNotSentForReview" not in str(e):
            raise
        # Apps with managed publishing on must send changes for review by hand.
        edits.commit(**ids, changesNotSentForReview=True).execute()
        print("Saved. Send the changes for review in the Play Console (Publishing overview).")
    print("Done.")


def main():
    aab, store = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    api = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    track = os.environ.get("TRACK") or "alpha"
    args = (api, aab, store, track,
            os.environ.get("SEND_BUNDLE") != "false", os.environ.get("SEND_LISTING") != "false")
    try:
        send(*args, "completed")
    except HttpError as e:
        # An app Google hasn't reviewed yet only takes draft releases.
        if "draft" not in str(e).lower():
            if "track" in str(e).lower():
                print("Tracks this app has:", [t["track"] for t in tracks(api)])
            raise
        print("Play only takes a draft release for this app, so sending it as a draft.")
        send(*args, "draft")


def tracks(api):
    edit = api.edits().insert(packageName=PACKAGE, body={}).execute()["id"]
    return api.edits().tracks().list(packageName=PACKAGE, editId=edit).execute().get("tracks", [])


if __name__ == "__main__":
    main()
