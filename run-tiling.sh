#!/bin/bash
# HPR/SWAY launcher for V.E.C.T.O.R desktop app

APP="vector-1.0.0.jar"

# Get current window manager
if whichHyprland >/dev/null 2>&1; then
    WM="hyprland"
elif which sway >/dev/null 2>&1; then
    WM="sway"
else
    WM=""
fi

# Run app in background
java -jar "$APP" --app &
PID=$!

# Wait for window to appear and float it
sleep 1

case $WM in
    hyprland)
        hyprctl dispatch float "pid:$PID"
        ;;
    sway)
        swaymsg "[pid=$PID] floating enable"
        ;;
esac

echo "V.E.C.T.O.R running (PID: $PID)"