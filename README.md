# Risk Dice Simulator

Android simulator bitk Risk v Javi. Videz in vedenje sta prenesena iz v0.dev prototipa
(`risk-dice-simulator-app.zip`, Next.js): temna tema, pisavi Geist in Geist Mono,
3D kocke z animacijo, dnevnik bitke in točne verjetnosti. Deluje lokalno, brez strežnika.

## Uporaba

1. Nastavi vojski s tipkovnico ali z gumbi −10/−1/+1/+10 (največ 2.000 na stran).
   Sprememba vojsk začne novo bitko; **Reset** vrne zadnji začetni scenarij.
2. **Capital defending** dovoli branilcu do 3 kocke.
3. **Roll once** vrže en krog, **Roll all** odigra bitko do konca. Napadalec vedno
   zadrži eno vojsko; izenačenje zmaga branilec.
4. **Battle log** hrani zadnjih 500 vnosov. Pri »Roll all« lahko razpreš vse kroge.
5. **Odds to finish the battle** prikaže točno verjetnost zmage in pričakovane
   preostale vojske (dinamično programiranje, ne simulacija) ter izide naslednjega meta.

## Gradnja in preverjanje

Potrebna sta JDK 21 in Android SDK 36.1.

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Testi preverjajo pravila (36 kombinacij para, točne verjetnosti 3 proti 2, privzeti
scenarij 87,3 %), največji scenarij 2.000 : 2.000, vnos, gumbe, dnevnik in ohranitev
stanja ob vrtenju (Robolectric, API 28).

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Datoteke

- `RiskEngine.java`: pravila, met kock in točne verjetnosti.
- `BattleViewModel.java`: stanje zaslona, dnevnik in izračun verjetnosti v ozadju.
- `MainActivity.java`: prikaz in vnos.
- `DieView`, `DiceStackView`, `RiskSwitch`, `BarView`, `ArenaBackground`: risani elementi
  v0 dizajna. `CssTextView`/`CssEditText` posnemata CSS `line-height`.
- `res/font`: Geist in Geist Mono (SIL Open Font License, Google Fonts).
- Ikone so iz lucide (ISC).
