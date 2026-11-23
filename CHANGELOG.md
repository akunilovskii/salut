# Salut releases

## Unreleased

- **Phone notices:** Bottom notices are centered at 80% of the available window width, 12 dp above system navigation or the safe bottom edge, independent of app navigation and the mini-player. Long text wraps while actions remain accessible. Resizing keeps the width and bottom gap without adding artificial safe-area offsets.

- **Feedback form:** Error/Improvement selection switches instantly without animation or background highlight; only the radio indicator changes; Description cursor and multiline text start at the top.

- **Feedback:** Send errors and improvements from a circular speech-bubble toolbar icon. Type uses one radio group, while Screen is an optional dropdown with no automatic selection. All fields have persistent labels and consistent spacing, Description is twice as tall, instructions are shorter, and the Function field is removed. Drafts survive closing the form, backgrounding and failed delivery within the current app session. Ending the app session or successful delivery clears them. Unchanged retries keep their original message and ID. Closing cancels a pending request, and delivery clears the draft only after a matching receipt.

- **Playlists open directly below the player:** Show the loaded playlist directly below the player, with the current video marked. The heading includes its position, playlist size, owner and privacy icon. Removed the collapsed playlist card and unrelated loading placeholders.
- **The current video starts below the playlist heading:** Opening a playlist positions the selected row at the top of the list. Near the end, scrolling stops at the last row without adding empty space.
- **Pull down to reveal video details:** Playlist pages initially show the list. Pulling down past the first row reveals video information, actions and comments. Scrolling up hides that information before moving the list.
- **One-line video titles with an edge fade:** Long titles stay on one line and fade at the edge. A second line shows channel and video details, with a real channel handle when available. Tap more to read the full title, metadata and description.
- **Independent repeat and shuffle controls:** Buttons beside the playlist title control repeat and shuffle for the current session. Repeat the playlist or one video while keeping shuffle independent. Global settings stay unchanged. Next and autoplay follow the source and stop at the end when repeat is off; Play next retains priority and returns to the source afterward.
- **Clearer recommendation settings:** Renamed the recommendation setting and clarified which video sources it affects.

## 1.18.0

- **Salut public release:** player, channel, playlist and interface improvements based on NewTube 1.17.1.
- **Updates from GitHub:** check for releases, download an update and install it with Android confirmation.
- **Playback fixes:** closing system picture-in-picture also closes the underlying mini-player session.
- **Manual YouTube link setup:** a guide for choosing Salut as the handler for YouTube links.
- **Quality selection:** compatible NewTube fixes preserve per-video choices and adaptive defaults.
