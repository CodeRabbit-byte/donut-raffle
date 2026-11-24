# Donut Raffle

A client-side Fabric mod for **Minecraft 1.21.11** that helps you host a pay-to-enter jackpot on DonutSMP.

## What it does

- **Reads payments.** When someone pays you (`NAME paid you $X.`), each whole spot price gives them the
  next open spot. Paying 3x the price takes 3 spots.
- **Shows the jackpot at the top of the screen.** The bar shows the prize item, the spot price and what
  the winner gets. Each spot shows `#1`, the player's face and their name, plus the pot. The bar shrinks
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

| In the menu | |
|---|---|
| Winner: spot N / Empty spot | Pick the selected spot as the winner, or free it |
| Add by hand | Give the next spot to a player whose payment was missed |
| Change prize | Search every item by name (elytra, dragon head, ...) and add an optional custom label |
| Prize worth | Type what the prize is worth and the spot price is worked out: an elytra worth `81m` with 9 spots at 90% = `10m` a spot. It follows spot and Win % changes. Typing a price by hand turns it off |
| Price / Win % | Spot price (`10m`, `2.5m`) and the winner's share (`87`, `90%`, or `8.1x`). Both apply as you type |
| Spots - / + | How many spots are open, 1 to 9 |
| Settings | Tax %, bar size and position, owed-back list on or off |

Commands: `/raffle status`, `price <amount>`, `worth <amount | off>`, `spots <1-9>`, `payout <87 | 8.1x>`, `tax <percent>`,
`prize [item]`, `winner <spot>`, `unpick`, `add <player>`, `clear <spot>`, `pause`, `open`, `paid`,
`new`, `unqueue <n>`, `dismiss <id>`, `settings`.

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
