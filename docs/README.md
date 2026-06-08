# Computer Mod Documentation

A self-contained documentation site for the Computer Mod. It covers the mod from the ground up:
programming in Lua, every block and item, every function, and how to read any block from any mod or
Create addon.

## Files

- `index.html` is the whole site in one self-contained file, with no build step and no internet needed.
  It renders the embedded Markdown into navigable pages, with Lua syntax highlighting, search, and
  copy-as-Markdown buttons.
- `FULL_DOCS.md` is the same documentation as a single Markdown file, handy for reading on GitHub or
  feeding to an LLM. It is generated from `index.html`, and the two are kept identical.

## Preview it locally

Double-click `index.html`. It works straight from disk (`file://`), offline.

## Publish it with GitHub Pages

GitHub hosts the site for free, with no server to run:

1. Create a GitHub repo and put this whole project in it. The site lives in `docs/`.
2. On GitHub, open Settings, then Pages.
3. Under Build and deployment, Source, choose Deploy from a branch.
4. Set Branch to `main` and the folder to `/docs`, then Save.
5. After a minute the site is live at `https://<your-username>.github.io/<your-repo>/`.

Any time you edit `index.html`, regenerate the Markdown so the two stay in sync:

```bash
python3 - <<'PY'
import re
html=open('docs/index.html',encoding='utf-8').read()
md=re.search(r'<script type="text/markdown" id="docs">\n?(.*?)</script>',html,re.S).group(1).strip('\n')+'\n'
open('docs/FULL_DOCS.md','w',encoding='utf-8').write(md)
PY
```

## Link it from inside the game

The mod's GUIs (the computer screen, the Sensor and Receiver screens, and the Controller config screen)
have a Wiki button that is disabled until a URL is set. Once your Pages site is live, set the
`WIKI_URL` constant to your published address and rebuild the mod:

- `src/main/java/com/computermod/client/ComputerScreen.java`
- `src/main/java/com/computermod/client/ChannelScreen.java`
- `src/main/java/com/computermod/client/ControllerConfigScreen.java`

Set each to `https://<your-username>.github.io/<your-repo>/` and the in-game buttons open the site.

## The copy-for-LLM button

The header button Copy all docs copies the entire site to your clipboard as clean Markdown. Paste it
into an LLM and ask it to write a program. The text lists every function that exists, so the model
stays within the real API.
