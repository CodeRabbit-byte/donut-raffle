# Donut Raffle

A client-side Fabric mod for **Minecraft 1.21.11** that helps you host a pay-to-enter jackpot on DonutSMP.

## What it does

- **Reads payments.** When someone pays you (`NAME paid you $X.`), each whole spot price gives them the
  next open spot. Paying 3x the price takes 3 spots.
- **Shows the jackpot at the top of the screen.** The bar shows "JACKPOT" with the prize item's icon,
  the spot price and what the winner gets. Each spot shows `#1`, the player's face and their name, plus the pot. The bar shrinks
  to fit small screens.
- **Puts late payers in the next round.** Money that arrives after the spots are full, after the winner
  is picked, or after the payout goes into a "Next round" queue. Those players fill the first spots of
  the next round.
- **Works out the payout.** You decide the winner in-game. The winner gets a share of the pot (90% by
  default, so 8.1x the price with 9 spots), minus 2% tax on that share. A full 9 x $1M round pays
  **$7,938,000**.
- **Spots the winner from your payment.** Mark the winner's spot yourself, or just `/pay` them the
  jackpot and the mod marks them. Their spot turns gold when picked and green once paid.
- **Never pays for you.** "Fill /pay" types `/pay NAME 7938000` into your chat box, and you press Enter.
- **Tracks refunds.** Money that doesn't buy a spot (less than the price, part of a spot, entries paused)
  goes on an "owed back" list with a refund button. Your own `You paid` messages clear the matching entry.

## Using it

Press **H** (rebindable) or type `/raffle` to open the menu. Click a spot on the bar to select it.

To switch the whole mod off (no payment tracking, no bar), use the **Raffle: ON/OFF** button in the
menu or `/raffle off`, and `/raffle on` to switch it back. You can also bind a key to
"Donut Raffle on/off" in Controls.

| In the menu | |
|---|---|
| Winner: spot N / Empty spot | Pick the selected spot as the winner, or free it |
| Add by hand | Give the next spot to a player whose payment was missed |
| Change prize | Search every item by name (elytra, dragon head, ...) and add an optional custom label |
| Price / Win % | Spot price (`10m`, `2.5m`) and the winner's share (`87`, `90%`, or `8.1x`). Both apply as you type |
| Spots - / + | How many spots are open, 1 to 9 |
| Settings | Tax %, owed-back list on or off, and **Move / resize bar** |

Commands: `/raffle status`, `on`, `off`, `toggle`, `hud`, `price <amount>`, `spots <1-9>`, `payout <87 | 8.1x>`, `tax <percent>`,
`prize [item]`, `winner <spot>`, `unpick`, `add <player>`, `clear <spot>`, `pause`, `open`, `paid`,
`new`, `unqueue <n>`, `dismiss <id>`, `settings`.

### Moving and resizing the bar

Open **Settings > Move / resize bar** (or `/raffle hud`). Over the live game you can:

- **Drag** the bar to move it. It snaps to the centre line and the top edge.
- **Scroll** to resize it.
- **Right-click** it to hide or show it.
- Use the **arrow keys** to nudge it by one pixel.
- Hold **Shift** while dragging to turn off snapping.

**Reset sizes** puts it back at the top centre, at full size.

Everything is saved in `config/donutraffle/raffle.json`.

## Building

Needs Java 21.

```
./gradlew build                 # build/libs/donutraffle-<version>.jar, runs the unit tests
./gradlew runClientGameTest     # plays a full round in a real client and saves screenshots
```

Requires Fabric Loader 0.19.3+ and Fabric API for 1.21.11.

## License

MIT
