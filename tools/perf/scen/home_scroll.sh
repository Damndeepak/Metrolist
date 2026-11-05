# five flings down Home and six back up (250ms swipes, shorter ones land as taps)
for i in 1 2 3 4 5; do adb shell input swipe 540 1700 540 500 250; sleep 1.2; done
for i in 1 2 3 4 5 6; do adb shell input swipe 540 600 540 1800 250; sleep 1.2; done
