# rotary-telegram
repo for client side Where Are You app

Add google_maps_key entry for both debug and release values (do NOT commit!):
1) Right-click app, select New\Android Resource Directory
2) Source set = debug
3) OK
4) Create a new file, google_maps_api.xml
5) Add:
<resources>
    <string name="google_maps_key">[API_KEY]</string>
</resources>
6) Replace API_KEY with value from console.developers.google.com/apis
7) Repeat for again for the RELEASE configuration

Make sure google-services.json is up-to-date
1) Open Gradle tool window
2) Right-click app\Tasks\android\signingReport, select Run
3) Make sure SHA1 key is included in the SHA certificate fingerprints list; console.firebase.google.com
  a) Download the google-services.json (copy under app) if the SHA1 key has been updated