# Troubleshooting

## ADB or Shizuku refuses to grant log access

An error containing `Neither user 2000` and `GRANT_RUNTIME_PERMISSIONS` means ColorOS is blocking the shell from granting permissions.

1. Open Developer options on the phone.
2. Turn on **Disable permission monitoring**. Newer firmware may call it **Disable system optimization**.
3. If neither setting appears, switch the system language to English (United States) for a moment and reopen Developer options.
4. Read the warning before you accept. This setting relaxes manufacturer restrictions and may increase background power use.
5. Restart the phone. On ColorOS 16 the setting takes effect only after a restart. Then grant again.

If the setting is still missing, include your ColorOS version when you [report the problem](https://github.com/heykay-47/SnapKeyMap/issues/new?template=bug_report.yml).

## The key stops working after a restart

Open SnapKey Map and allow device-log access again. If the app shows **Paused**, tap **Connect**. You do not need to repeat the ADB grant unless you uninstalled the app or revoked the permission.

If you declined the log-access prompt, wait a minute before you try again. Check that the accessibility service is still on and that ColorOS allows background activity.

## A double press runs the single-press action

Set Oppo's own Snap Key action to None. Keep SnapKey Map open, double-press the key, and read the **Last:** text.

- `press` means the app did not recognise the double press. Update to v1.0.3 or later.
- `double press` means detection worked. Check the assigned action and that the selected app is still installed.

When you report a detection problem, include your app version, ColorOS version, assigned actions, and the text under **Last:**. Do not share an unfiltered system log. Device logs can contain sensitive information.

For setup steps, see the [setup guide](setup.md).
