# tap the mini player, then drag the player closed, three times (1080x2400 coordinates)
for i in 1 2 3; do adb shell input tap 400 2010; sleep 2.5; adb shell input swipe 540 500 540 2100 300; sleep 2.5; done
