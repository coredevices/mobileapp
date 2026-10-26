// Subscribing is the on/off switch: it starts the phone ringing (the native find-my-phone source
// rings while anyone is subscribed), and the subscription ending when this app closes stops it.
Pebble.addEventListener("ready", function () {
    Pebble.subscribeToSource({
        category: "phone_state",
        item: "find_my_phone",
        onData: function () {},
        onError: function (err) {
            console.log("find_my_phone subscription failed: " + JSON.stringify(err));
        },
    });
});
