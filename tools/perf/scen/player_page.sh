# open the player, tap the bottom card to grow the lyrics or queue page, back out twice
for i in 1 2; do adb shell input tap 400 2010; sleep 2.5; adb shell input tap 540 2330; sleep 2.5; adb shell input keyevent 4; sleep 2.5; adb shell input keyevent 4; sleep 2.5; done
