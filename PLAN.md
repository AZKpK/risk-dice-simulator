# Risk Dice Simulator — analiza in načrt izboljšav

> **Opomba (1. 10. 2026):** Zaslon je zamenjan z dizajnom iz v0.dev. Revizijske funkcije
> iz tega načrta (seed, ponovitev metov, paketne simulacije, SQLite zgodovina, JSON/CSV izvoz)
> so odstranjene; namesto ocene s simulacijami aplikacija računa točne verjetnosti.
> Trenutno stanje opisuje [README.md](README.md).

Datum: 1. oktober 2026.

## Obseg pregleda

Pregledan je lokalni Android repozitorij: obe produkcijski datoteki Java, XML zaslon,
teme, konfiguracija Gradle, manifest in obstoječa testa. Aplikacija uporablja Javo,
Android Views in temo Material 3. Ločitev `RiskEngine` od `MainActivity` je dobra osnova.
V pregledani kodi ni klicev jezikovnega modela ali strežnika. Kocke generira
`java.util.Random` z `nextInt(6) + 1`.

To je statičen pregled. `bash gradlew --version` ni uspel, ker okolje nima ukaza
`java` in nastavljenega `JAVA_HOME`. Gradnje, testov in videza na napravi zato nisem
preveril. Dostop do lokalnega repozitorija ne pomeni dostopa do morebitnih drugih
vej na strežniku, zasebnih storitev ali uporabnikove naprave.

## Ugotovitve ob prvem pregledu

| Prioriteta | Ugotovitev | Mesto | Posledica |
| --- | --- | --- | --- |
| P0 | Vrednosti kock se po krogu zavržejo. | `RiskEngine.java`, `rollOnce`, vrstice 38–52 | Končnega rezultata ni mogoče preveriti po metih. |
| P0 | `Integer.parseInt` nima obravnave napake ali zgornje omejitve. | `MainActivity.java`, vrstice 96–97 | Predolg vnos, npr. `2147483648`, lahko povzroči izjemo. |
| P0 | Celotna bitka in 1.000 simulacij tečeta v poslušalcu gumba. | `MainActivity.java`, vrstice 50–69 | Pri večjih vojskah lahko izračun blokira odzivnost zaslona. |
| P1 | Povprečji se izračunata s celoštevilskim deljenjem. | `RiskEngine.java`, vrstice 85–87 | Decimalni del se odreže; prikazana statistika izgubi natančnost. |
| P1 | Vsoti preživelih sta tipa `int`. | `RiskEngine.java`, vrstice 69–82 | Pri velikih vojskah in veliko ponovitvah je mogoče prekoračiti obseg. |
| P1 | Javni API ne preveri števila simulacij. | `RiskEngine.java`, `runBatchSimulation` | Nič ponovitev povzroči deljenje z nič; UI trenutno vedno poda 1.000. |
| P1 | En met spremeni vhodni polji, celotna bitka pa ne. | `MainActivity.java`, vrstice 42–59 | Pomen naslednjega klika je odvisen od izbrane akcije. |
| P1 | Ni izrecnega modela za ohranitev rezultata in zgodovine. | `MainActivity.java`, `onCreate` | Ohranitev seje ob ponovnem ustvarjanju zaslona ni načrtovana. |
| P1 | Testa preverjata le `2 + 2` in ime paketa. | `app/src/test`, `app/src/androidTest` | Pravila bitke in statistika nimajo vsebinskega testnega pokritja. |
| P2 | Besedila in barve rezultata so večinoma v kodi oziroma layoutu. | `MainActivity.java`, `activity_main.xml` | Lokalizacija in skladen temni način sta otežena. |

Motor trenutno samodejno uporabi največje dovoljeno število kock, napadalcu pusti
eno vojsko, kocke primerja padajoče in izenačenje pripiše branilcu. Način Capital
poveča največje število obrambnih kock na tri. To vedenje je treba izrecno opisati
kot pravila te aplikacije; podprte različice igre lahko kasneje dobijo ločene nastavitve.

## 1. Zanesljiv motor in zapis dejanskih metov

- Ločiti nespremenljive rezultate: `RoundResult`, `BattleResult`, `SimulationStats`.
- `RoundResult` naj vsebuje začetni vojski, izvirni vrstni red obeh nizov kock,
  razvrščene kocke, primerjave parov, izgube in končni vojski. Neporabljene kocke
  morajo ostati vidne. Podatke zajeti neposredno ob prvem metu.
- Celotna bitka naj vrne urejeno zaporedje teh krogov. Vsak naslednji krog začne
  s končnim stanjem prejšnjega. UI naj prikazuje podatke tega istega rezultata.
- Za vsako novo bitko oziroma paket določiti lasten seed in lastno instanco RNG.
  Zapisati začetni vnos, pravila, algoritem RNG, seed, različico motorja in vrstni red
  generiranja kock. Sedanja skupna instanca RNG ni primerna za samostojno ponovljivost
  posameznega klika brez dodatnega upravljanja njenega stanja.
- Dodati »Ponovi isti izračun«: enak seed, pravila, vhod in različica morajo dati
  enake mete in rezultat. Ponovitev ne sme prepisati originalnega zapisa.
- Preverjanje zapisa naj iz shranjenih kock ponovno izračuna izgube in končni izid.
  To preveri konsistentnost izračuna, samo po sebi pa ne dokazuje nepristranskosti RNG.
- Dodati JSON izvoz z vsemi podatki; CSV kot berljiv povzetek krogov.

Seed omogoča ponovljivost, ne dokazuje fizičnih metov ali neodvisne poštenosti.
Tudi hash izvoza bi bil zgolj kontrolna vsota, če ni neodvisno potrjen. Za želeni
pregled je najpomembnejši dejanski zapis kock in sledljiv izračun.

## 2. Varnost vnosa, odzivnost in statistika

- Preverjati vnos tudi v motorju: veljavne začetne vojske, pozitivno število
  ponovitev in dogovorjene omejitve obremenitve. Zaključeno bitko obravnavati kot
  stanje, ne kot nov neveljaven uporabnikov vnos.
- Napake prikazati ob ustreznem vnosnem polju in prestreči napake pretvorbe števil.
- Celotne bitke in pakete izvajati prek `ExecutorService`; podatke zaslona voditi
  v `ViewModel`. Napredek posodabljati v intervalih, omogočiti preklic in preprečiti
  sočasna zagona iste akcije. Preklican paket mora biti označen kot delni rezultat.
- Za vsote uporabiti `long`, za povprečja `double`. Ločiti povprečje vseh bitk od
  povprečja ob zmagi; jasno označiti, da napadalčev rezultat vključuje zadržano vojsko.
- Najprej prikazati število zmag, število dejansko končanih simulacij in odstotke.
  Kasneje dodati interval zaupanja za ocenjeno verjetnost zmage.
- Po meritvi časa in alokacij razmisliti o `int[]` namesto `ArrayList<Integer>` za
  največ tri kocke. Prednost ima odprava blokiranja UI, nato mikrooptimizacije.
- Ne uvajati vnaprej izračunanih prehodov kot nadomestila za dejanske mete v načinu
  s sledjo. Morebitni hitri statistični način mora biti jasno ločen in označen.

## 3. Uporabniški vmesnik

Ohraniti obstoječo Javo in XML; prenova ne zahteva prehoda na Compose.

- Zgoraj dve kartici: Napadalec in Branilec, jasno označeni števili vojsk ter
  informacija, da napadalec eno vojsko zadrži. Barve dopolniti z besedilom in ikonami.
- Material vnosna polja z napakami ob polju, smiselne začetne vrednosti, možnost
  ponastavitve ter izbira Standard / Capital s kratko razlago pravil.
- Tri jasno poimenovane akcije: »Vrzi en krog«, »Odigraj bitko«, »Oceni možnosti«.
  Prvi dve nadaljujeta isto aktivno bitko; statistika uporablja ločen scenarij.
- Rezultat prikazati v kartici: zmagovalec oziroma stanje bitke, preživele vojske,
  izgube in število krogov. Prvotni scenarij in trenutno stanje naj bosta razločljiva.
- Pod rezultatom razširljiv »Pregled metov«: vsak krog prikazati v dveh vrsticah,
  napadalca zgoraj in branilca neposredno pod njim. Stolpci naj vsebujejo začetno
  število vojsk, kocke, izgube in preostale vojske.
- Kocke razvrstiti padajoče in vsako postaviti v svoj enako širok stolpec, tako da
  sta primerjani kocki navpično poravnani. Zmagovalno kocko vsakega para prikazati
  krepko; pri izenačenju označiti branilčevo. Kocko brez para prikazati manj poudarjeno,
  manjkajočo pa z »—«. Pravilo izenačenja pojasniti enkrat v legendi.
- Zgodovino prikazati z `RecyclerView`, ne z enim dolgim `TextView`.
- Uporabiti barve teme za svetli in temni način; vsa besedila preseliti v
  `strings.xml`. Preveriti povečano pisavo, TalkBack, dotikalne površine in odmike
  ob sistemskih vrsticah na ciljnih različicah Androida.
- Animacija kock naj se konča s prej izračunanimi in shranjenimi vrednostmi.
  Animacija ne sme sprožiti drugega meta.

Primer prikaza, samo ponazoritev — to ni dejanski met trenutne aplikacije:

Krog 1

| Stran | Na začetku | Kocka 1 | Kocka 2 | Kocka 3 | Izgubi | Ostane |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Napadalec | 10 | **6** | 4 | 2 | −1 | 9 |
| Branilec | 8 | 5 | **4** | — | −1 | 7 |

**Krepko** = zmagovalna kocka; pri izenačenju zmaga branilec.
Trije stolpci kock v aplikaciji tvorijo eno skupino »Kocke«. Izvirni vrstni red
metov ostane shranjen za preverjanje in izvoz; osnovni prikaz pokaže razvrščene kocke.

## 4. Pregled paketnih simulacij in zgodovina

- Pri paketih prikazati seznam posameznih bitk z začetnim scenarijem, izidom in
  številom krogov. Izračun agregata mora izhajati iz teh istih rezultatov.
- Za začetno omejeno število simulacij omogočiti pregled originalnih krogov.
  Pred zvišanjem omejitev določiti in izmeriti proračun pomnilnika.
- Pri večjih paketih originalne zapise sproti shranjevati na disk, pregled pa
  nalagati postopoma. Trajno zgodovino po potrebi voditi v Room/SQLite.
- Če način hrani samo povzetek in seed, kasnejši pregled označiti kot ponovljen
  izračun, ne kot originalni dnevnik metov. Ne prikazovati izbranega vzorca kot
  popoln pregled vseh simulacij.
- Zgodovini dodati ponovno uporabo scenarija, ponovitev s seedom in izvoz.
  Shranjevanje velike zgodovine naj ima dogovorjene omejitve in možnost čiščenja.

## Predlagano zaporedje izvedbe in merila sprejema

| Korak | Obseg | Kako preverimo uspeh |
| --- | --- | --- |
| 1 | Validacija, tipi rezultatov, decimalna povprečja, testni RNG | Testi izenačenj, omejitev kock, zaključka bitke, napačnega vnosa in povprečij. |
| 2 | Originalni krogi, seed, pregled metov, JSON izvoz | Ponovitev da enake mete; iz shranjenih kock dobimo iste izgube in izid. |
| 3 | Izračun v ozadju, preklic, stanje seje | UI ostane odziven; vrtenje naprave ohrani sejo; preklic jasno označi delni izid. |
| 4 | Material kartice, poravnan prikaz kock v dveh vrsticah, tema in dostopnost | Pregled na napravi v obeh temah, pri povečani pisavi in s TalkBack. |
| 5 | Paketni dnevnik, zgodovina in meritve zmogljivosti | Vsota zmag se ujema s številom končanih bitk; izvoz in prikaz se ujemata. |

Dodatni testi motorja: enake vhodne kocke dajo enak rezultat; izgube so enake
številu primerjanih parov; stanje med krogi je zvezno; napadalec ostane z najmanj
eno vojsko. Za neodvisno preverjanje pravila enega para izčrpno preveriti vseh 36
kombinacij: napadalec zmaga v 15 primerih. Standard in Capital testirati ločeno.
Statistični naključni testi ne smejo nadomestiti determinističnih testov pravil.

Predlagani prvi uporabni MVP zajema korake 1–4, s pregledom enega kroga in celotne
bitke. Popoln pregled vseh metov paketne simulacije zaključi korak 5.

## Reference za tehnične predloge

- [Android: delo v ozadju z Java threads](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads)
- [Android: ViewModel in ohranjanje stanja](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Java 11: Random in ponovljivost s seedom](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Random.html)

## Stanje izvedbe

Implementirani so vsi koraki načrta:

- Motor uporablja ločene nespremenljive rezultate, validacijo, decimalna povprečja,
  vsote `long` in deterministične teste pravil. Dodan je Wilsonov 95-odstotni interval.
- Vsak originalni krog hrani izvirni in razvrščeni vrstni red kock. UI uporablja
  dogovorjeni dve vrstici z navpično poravnanimi pari, krepkimi zmagovalnimi kockami
  in manj poudarjenimi kockami brez para.
- Seed je samodejen in skrit na glavnem zaslonu, viden pa v podrobnostih in izvozu.
  Ponovitev ustvari ločen označen zapis s povezavo do izvirne bitke.
- Izračuni tečejo v `ExecutorService`; `ViewModel` ohrani stanje pri vrtenju zaslona.
  Preklic ohrani delni zapis. SQLite omogoča tudi obnovitev shranjene aktivne bitke.
- Prenovljen je Material UI s karticama vojsk, napakami ob vnosu, svetlim/temnim
  načinom, slovenščino ter opisi kock za TalkBack.
- Paketi sproti shranjujejo originalne sledi na disk. Seznam bitk je razdeljen na
  strani po 50; posamezna bitka odpre dejanske shranjene kroge. Ocena šteje le
  končane bitke, morebitna prekinjena zadnja bitka pa ostane vidna v dnevniku.
- JSON in CSV izvoz zajameta celoten scenarij, ne samo trenutno odprte bitke ali
  strani. Shranjuje se zadnjih 10 scenarijev, potrebni izvirniki ponovitev in aktivna
  bitka; dodana sta ponovna uporaba scenarija in čiščenje zgodovine.
- Omejitve so 1.000 vojsk na stran, največ 10.000 simulacij in največ 200.000
  potencialnih krogov na paket. UI razloži dovoljeno število ponovitev.

Gradnja APK in testi so bili izvedeni z JDK 21 in Android SDK 36.1, nameščenima v
začasnem imeniku. Preverjenih je 27 testov, vključno s pravili, shranjevanjem,
izvozom, preklicem, poravnavo/oznakami kock, temnim načinom in obnovo seje.

Vizualni pregled na fizični napravi, dejanski TalkBack in sistemski izbirnik datotek
ostajajo za preverjanje na napravi; lokalni Android testi uporabljajo Robolectric
API 28. Ob nenadni ustavitvi procesa med dolgo bitko se obnovi zadnji checkpoint,
ki se med izračunom zapiše vsakih 25 krogov. Podrobnosti in izmerjeni časi motorja
so v [README.md](README.md).
