// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

process.on('unhandledRejection', up => { throw up });

/*
 * Using onCall
 */
exports.friendDeleted = functions.https.onCall((data, context) => {

  // data contains the message text
  // context parameters represent user auth information

});

/*
 * Using triggers
 *
// Listens for a friend object to be removed. When it is, this will remove the corresponding user account
// from the user object that was removed.
exports.friendDeleted = functions.firestore.document(`/Users/{userId}/FriendList/{friendId}`).onDelete((snap, context) => {

  var userId = context.params.userId;
  var friendId = context.params.friendId;

  // remove old request object from requester's list
  admin.firestore().doc(`/Users/${friendId}/FriendList/${userId}`).get().then(friend => {
    if (friend.exists) {
      console.log("Deleting Users/", friendId, "/FriendList/", userId);
      return friend.ref.delete();
    } else {
      return console.log("Users/", friendId, "/FriendList/", userId, "does not exist.");
    }
  }).catch(err => {
    return Promise.reject(err);
  });

  return Promise;
})

// Listens for updates to the IsAccepted field on a friend, then updates the IsAccepted value for
// this user under the friend's friend list.
exports.friendListUpdated = functions.firestore.document(`/Users/{userId}/FriendList/{friendId}`).onUpdate((change, context) => {

  var isAccepted = change.after.data().IsAccepted;
  if ((isAccepted !== change.before.data().IsAccepted)) {
    var userId = context.params.userId;
    var friendId = context.params.friendId;
    admin.firestore().doc(`/Users/${friendId}/FriendList/${userId}`).get().then(friend => {
      if (friend.exists) {
        console.log("Setting User/", friendId, "/FriendList/", userId, "/{IsAccepted:", isAccepted, "}");
        return friend.ref.set({"IsAccepted": isAccepted},{merge:true});
      } else {
        return console.log("Users/", friendId, "/FriendList/", userId, " does not exist.");
      }
    }).catch(err => {
      return Promise.reject(err);
    });
  } else {
    console.log("No monitored changes detected.");
  }

  // TODO: add additional document property changes as needed
  return Promise;
})

// Listens for a new friend request to be created. When it is, this will attempt to locate the requested user and update
// that users friend list with a request. If that user exists, grab the requested friends uid and update this entry. If the
// user doesn't exist yet, save for later processing (more than likely onUserCreated will pick up and process the request).
exports.friendRequestCreated = functions.firestore.document(`/Users/{userId}/FriendList/{sanitizedEmail}`).onCreate((snap, context) => {

  var userId = context.params.userId;
  var friendEmailSanitized = context.params.sanitizedEmail;
  var requesterEmail = null;
  var requesterFullName = null;
  var friendId = null;
  var friendEmail = snap.data().Email;
  var friendFullName = snap.data().FullName;

  // look for the friendEmailSanitized in our collection of users (if not found, no further processing necessary for now)
  return admin.firestore().collection(`Users`).get().then(users => {
    users.forEach(user => {
      if (user.id === userId) {
        requesterEmail = user.data().Email;
        requesterFullName = user.data().FullName;
      } else if (user.data().Email === friendEmail) {
        friendId = user.id;
      }
    });

    return Promise;
  }).then(snapshot => {
    if (friendId !== null && requesterEmail !== null && requesterFullName !== null) {
      // add friend object under friendId for requester object
      console.log("Adding Users/", friendId, "/FriendList/", userId, "{Email:", requesterEmail, "}");
      const promises = [];
      promises.push(new Promise((resolve, reject) => {
        return admin.firestore().collection(`Users/${friendId}/FriendList`).doc(userId).set({
          "Email": requesterEmail,
          "FullName": requesterFullName,
          "IsAccepted": false,
          "IsDeclined": false,
          "IsPending": true,
          "IsSharing": false,
        }, {merge:true});
       }));

      // recreate the requester's object to reflect data about the friend and the status of the request
      console.log("Adding Users/", userId, "/FriendList/", friendId, "{Email:", friendEmail, "}");
      promises.push(new Promise((resolve, reject) => {
        return admin.firestore().collection(`Users/${userId}/FriendList`).doc(friendId).set({
          "Email": friendEmail,
          "FullName": friendFullName,
          "IsAccepted": true,
          "IsDeclined": false,
          "IsPending": false,
          "IsSharing": false,
        }, {merge:true});
      }));

      // remove old request object from requester's list
      promises.push(new Promise((resolve, reject) => {
        return admin.firestore().doc(`Users/${userId}/FriendList/${friendEmailSanitized}`).get().then(friend => {
          if (friend.exists) {
            console.log("Deleting Users/", userId, "/FriendList/", friendEmailSanitized);
            friend.ref.delete();
          }

          return Promise;
        }).catch(err => {
          throw err;
        });
      }));

      Promise.all(promises).then(results => {
        return console.log("User request tasks complete.");
      }).catch(err => {
        throw err;
      });
    } else {
      throw new Error("Could not find additional data for", userId);
    }

    return Promise;
  }).catch(err => {
     return Promise.reject(err);
  });
})

// Listens for new location entry being created for the user. It then gets the friends of the user and adds a
// new location item for each friends copy of this user.
exports.locationCreated = functions.firestore.document('Users/{userId}/LocationList/{locationId}').onCreate((snap, context) => {

  // perform desired operations ...
  var userId = context.params.userId;
  var locationId = context.params.locationId;
  var latitude = snap.data().Latitude;
  var longitude = snap.data().Longitude;

  // use the entries under FriendList for the user where the newly created object under LocationList
  const promises = [];
  var friendCollection = admin.firestore().collection(`Users/${userId}/FriendList`).where('IsAccepted', '==', true);
  friendCollection.get().then(friends => {
    friends.forEach(friend => {
      console.log("Adding Users/", friend.id, "/FriendList/", userId, "/LocationList/", locationId, "{Latitude:", latitude, ",Longitude:", longitude, "}");
      promises.push(new Promise((resolve, reject) => {
        admin.firestore().collection(`Users/${friend.id}/FriendList/${userId}/LocationList`).doc(locationId).set({
          "Latitude": latitude,
          "Longitude": longitude
        });
      }));
    });

    Promise.all(promises).then(results => {
      return console.log("Added location to users.");
    }).catch (err => {
      throw err;
    });

    return Promise;
  }).catch(err => {
    return Promise.reject(err);
  });
});

// Listens for a new user account to be created. When it is, this will look for any pending friend requests
// in other users to populate this users account. It will also create a pending friend request in the new
// users FriendList for each user discovered.
exports.userCreated = functions.firestore.document(`/Users/{userId}`).onCreate((snap, context) => {

  var newUserId = context.params.userId;
  var newEmail = snap.data().Email;
  var newEmailAsKey = snap.data().emailAsKey;
  var newFullName = snap.data().FullName;

  // scan all other users FriendLists for this objects newEmailAsKey and replace it with newUserId
  var usersRef = admin.firestore().collection(`Users`);
  var userQuery = usersRef.get().then(users => {
    users.forEach(user => {
      if (user.id !== newUserId) {
        console.log("Searching Users/", user.id, "/FriendList for", newEmailAsKey);
        var friendListRef = admin.firestore().collection(`Users/${user.id}/FriendList`);
        return friendListRef.get().then(friends => {
          friends.forEach(friend => {
            if (friend.data().emailAsKey === newEmailAsKey) {
              console.log("Adding Users/", user.id, "/FriendList/", newUserId);
              admin.firestore().collection(`Users/${user.id}/FriendList`).doc(newUserId).set({
                "Email": newEmail,
                "emailAsKey": newEmailAsKey,
                "FullName": newFullName,
                "IsAccepted": false,
                "IsDeclined": false,
                "IsPending": true,
                "IsSharing": false,
              },{merge:true});

              // add user to new users friend list for notifications
              console.log("Adding Users/", newUserId, "/FriendList/", user.id);
              admin.firestore().collection(`Users/${newUserId}/FriendList`).doc(user.id).set({
                "Email": user.data().Email,
                "emailAsKey": user.data().emailAsKey,
                "FullName": user.data().FullName,
                "IsAccepted": false,
                "IsDeclined": false,
                "IsPending": true,
                "IsSharing": false,
              },{merge:true});

              admin.firestore().collection(`Users/${user.id}/FriendList`).doc(newEmailAsKey).get().then(old => {
                if (old.exists) {
                  console.log("Deleting Users/", user.id, "/FriendList/", newEmailAsKey);
                  old.ref.delete();
                }

                return Promise;
              }).catch(err => {
                throw err;
              });
            } else {
              console.log(user.id, "does not have a request for", newEmailAsKey);
            }
          });

          return Promise;
        }).catch(err => {
          return Promise.reject(err);
        });
      } else {
        return console.log("Skipping Users/", user.id);
      }
    });

    return Promise;
  }).catch(err => {
    return Promise.reject(err);
  });
})

// Listens for updates to the fields of an user.
// If the IsSharing field is updated, the IsSharing property will be updated for all friends of this user.
exports.userUpdated = functions.firestore.document('Users/{userId}').onUpdate((change, context) => {

  var userId = context.params.userId;
  if (change.after.data().IsSharing !== change.before.data().IsSharing) {
    // user updated their sharing status; get the list of friends for this user (userId)
    var isSharingVal = change.after.data().IsSharing;
    const promises = [];
    admin.firestore().collection(`Users/${userId}/FriendList`).where('IsAccepted', '==', true).get().then(friends => {
      friends.forEach(friend => {
        console.log("Setting Users/", friend.id, "/FriendList/", userId, "{IsSharing:", isSharingVal, "}");
        promises.push(new Promise((resolve, reject) => {
          admin.firestore().doc(`Users/${friend.id}/FriendList/${userId}`).update({IsSharing: isSharingVal});
        }));
      });

      Promise.all(promises).then(results => {
        return console.log("Added location to users.");
      }).catch (err => {
        throw err;
      });

      return Promise;
    }).catch(err => {
      return Promise.reject(err);
    });
  } else {
    console.log("No monitored changes detected.");
  }

  // TODO: add additional document property changes as needed
  return Promise;
});
*/
