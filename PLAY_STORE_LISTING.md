# Google Play Store Listing — Screen Time Guard

## App name
Screen Time Guard

## Short description
Parental viewing limits and break countdowns for supported video apps.

## Full description
Screen Time Guard is a simple parental-control utility that helps parents and guardians manage viewing time on an Android device.

Set a viewing limit and a required break duration. When the viewing limit is reached, Screen Time Guard starts a break countdown. If YouTube or YouTube Kids is opened during the break, the app displays a clear Screen Time Guard break screen showing the remaining time. Other apps remain available.

Key features:
- Configurable viewing limit from 1 to 180 minutes
- Configurable break duration from 1 to 180 minutes
- Countdown showing exactly when viewing can resume
- Parent PIN to end a break early
- Ongoing notification while monitoring is enabled
- Monitoring resumes after device restart when enabled
- Settings and PIN remain on the device
- No ads, analytics, cloud account, or server required

Screen Time Guard uses Android Usage Access only to determine whether a supported video app is currently in the foreground. It uses Display over other apps only to show the parent-configured break screen over the supported video app during an active break.

Screen Time Guard is intended to be configured and controlled by a parent or guardian.

Screen Time Guard is an independent parental-control utility and is not affiliated with, sponsored by, or endorsed by YouTube or Google.

## Category
Parenting

## Target audience recommendation
Adults / parents and guardians (18+). Do not select children as the target audience unless you intentionally want the app evaluated under the Families policies.

## Data Safety draft
Data collected: None.
Data shared: None.
Data encrypted in transit: Not applicable — the app does not transmit user data.
Data deletion: Local app data can be deleted by clearing app storage or uninstalling the app.

## App access
No account or login is required.

## Ads
No ads.

## Content rating notes
Parental-control / screen-time utility. No user-generated content, gambling, violence, sexual content, or social features.

## Monitoring disclosure
The app is a child-monitoring / parental-control tool. Android manifest declares isMonitoringTool=child_monitoring.

## Foreground service declaration draft
Type: specialUse.
Purpose: Parent-enabled screen-time monitoring must continue while other apps are in use so the configured viewing limit and break period can be enforced.
User visibility: An ongoing notification clearly states that Screen Time Guard monitoring is active.
User control: The parent can stop monitoring from the app at any time.
Impact if interrupted: The viewing timer or break enforcement may stop or become inaccurate, defeating the parent-configured screen-time control.
