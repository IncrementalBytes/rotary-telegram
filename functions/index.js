// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions')

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin')
admin.initializeApp(functions.config().firebase)

// Listens for updates to the IsSharing field on a user, then updates the IsSharing value for
// all friends of this user.
exports.onIsSharingWrite = functions.database.ref(`/Users/{uid}/IsSharing`).onWrite((event) => {

  const previousVal = event.data.previous.val()
  const currentVal = event.data.val()
  if (currentVal !== previousVal) {
    const userId = event.params.uid
    const friendList_promise = admin.database().ref(`/Users/${userId}/FriendList`).once('value')
    return friendList_promise.then(snapshot => {
      snapshot.forEach(friend => {
        console.log("Setting IsSharing to", currentVal, "for", userId, "under", friend.key)
        admin.database().ref(`/Users/${friend.key}/FriendList/${userId}`).update({
          "IsSharing": currentVal
        })
      })
      return null
    }).catch(error => {
      console.error(error)
    })
  } else {
    console.log("Ignoring trigger because previous and new value are the same.")
    return null
  }
})

exports.onLocationUpdated = functions.database.ref(`/Users/{uid}/LocationList/{timestamp}`).onCreate((event) => {

  const previousVal = event.data.previous.val()
  const currentVal = event.data.val()
  if (currentVal !== previousVal) {
    const userId = event.params.uid
    const timestamp = event.params.timestamp
    const latitude = currentVal["Latitude"]
    const longitude = currentVal["Longitude"]
    const friendList_promise = admin.database().ref(`/Users/${userId}/FriendList`).once('value')
    return friendList_promise.then(snapshot => {
      snapshot.forEach(friend => {
        console.log("Adding", latitude, "by", longitude, "for", userId, "under", friend.key)
        admin.database().ref(`/Users/${friend.key}/FriendList/${userId}/LocationList/${timestamp}`).update({
                  "Latitude": latitude,
                  "Longitude": longitude
        })
      })
      return null
    }).catch(error => {
      console.error(error)
    })
  } else {
    console.log("Ignoring trigger because previous and new value are the same.")
    return null
  }
})
