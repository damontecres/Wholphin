# Intents

Wholphin accepts [intents](https://developer.android.com/guide/components/intents-filters) to load specified media.

Parameters can be specified as "extras" (eg `--es query value`) or as query parameters to the data URI (eg `-d wholphin://search?query=value`).

UUID parameters can be specified with or without hyphens.

## Server/user switching

You can choose a server and user to switch to with the `serverId` and `userId` UUID parameters. If the profile is not protected, the app will switch to it.

If not specified, the app will use the current user if auto sign in is enabled.

## Search

Action: `android.intent.action.SEARCH`

Shorthand action: `search`

Parameters:
- `query` - Optional, prepopulate the search field


### Examples

Full format
```bash
adb shell am start \
  -a android.intent.action.SEARCH \
  -n 'com.github.damontecres.wholphin/.MainActivity' \
  --es query "Avengers"
```

URI based
```bash
adb shell am start -d 'wholphin://search?query=Avengers'
```

## View

This opens the app to the specified item such as an episode or movie

Action: `android.intent.action.VIEW`

Shorthand action: `view`

Parameters:
- `itemId` - Required, the UUID of the media item


### Examples

Full format
```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -n 'com.github.damontecres.wholphin/.MainActivity' \
  --es itemId "5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d"
```

URI based
```bash
adb shell am start -d 'wholphin://view?itemId=5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d'
```

## Playback

Action: `com.github.damontecres.wholphin.PLAYBACK`

Shorthand action: `play`

Parameters:
- `itemId` - Required, the UUID of the media item
- `position` - Optional, the start position for playback in milliseconds

### Examples

Full format
```bash
adb shell am start \
  -a com.github.damontecres.wholphin.PLAYBACK \
  -n 'com.github.damontecres.wholphin/.MainActivity' \
  --es itemId "5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d"
```

URI based
```bash
adb shell am start -d 'wholphin://play?itemId=5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d'
```

## More examples

### Switch user

Full format
```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -n 'com.github.damontecres.wholphin/.MainActivity' \
  --es itemId "5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d" \
  --es serverId "341880f9-ca88-4038-8718-f6b2407950b1" \
  --es userId "5c8575e0-468d-44fb-8c70-00aa79160587"
```

URI based
```bash
adb shell am start -d 'wholphin://view?itemId=66c80aaac9c17d3761782d2205520229&serverId=341880f9-ca88-4038-8718-f6b2407950b1&userId=5c8575e0-468d-44fb-8c70-00aa79160587'
```

### Debug build

Debug builds have a different package, so you must specify the component by its full name:

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -n 'com.github.damontecres.wholphin.debug/com.github.damontecres.wholphin.MainActivity' \
  --es itemId "5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d"
```
