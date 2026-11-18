DAO's profile inputs

What `scripts/make_dao_profile.py` needs from the supported disc, so the game profile
(`dao.zip`) builds without the game (on GitHub's CI too). None of it is game code or data:

- `default.xex.sha256`: the hashes of the supported discs' `default.xex`, one per line; the phone
  checks the player's file against them. First the disc the translation is made from (title
  `454108C0`, media `186A1115`: English, Italian, Spanish), then the Russian disc (media
  `26111665`, "Dragon Age: Начало"), whose `default.xex` translates to the very same code: its
  XenonRecomp output with `config/DAO.toml` is byte for byte the first one's. Then the German
  disc (media `57EF80D8`, "Dragon Age: Origins (Germany)"), the same again.
- `ppc/ppc_config.h`: the image's layout, four addresses and sizes (the same kind of data as
  DAORecomp's `config/DAO.toml`).
- `ppc/ppc_context.h`: XenonRecomp's `ppc_context.h` template as its output carries it.
- `ppc_tu/`: the same two headers from the translation with Title Update 6 or 7
  (`config/DAO_tu6.toml`, the disc's `default.xex` with the title update's `default.xexp`),
  which the phone makes instead of the disc's when a title update is installed. Title Update 7
  for the disc above and the ones for the Russian and the German disc translate to
  byte-identical code.

The runtime is compiled against the two headers, and the phone checks that its own translation
gives the same. `make_dao_profile.py --refresh-inputs` writes `ppc/` again from `install/game`
and `recomp/ppc` after a new translation (`ppc_tu/` by hand, from a `DAO_tu6.toml` translation
without the `tu6_` prefix).
