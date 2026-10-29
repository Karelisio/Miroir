# Banque « De l'autre côté » — consignes de rédaction

Section de jeux à deux de Miroir. Un couple (adultes consentants, n'importe quel genre) joue depuis ses deux
téléphones ou en se passant un seul téléphone. Ton : **complice, drôle et tendre**. Jamais moralisateur,
jamais méchant, jamais vulgaire gratuitement.

## Fichiers

- `games/src/<categorie>-a.jsonl` : modes **devine** (Devine-moi et Duel) et **preferes** (Tu préfères).
- `games/src/<categorie>-b.jsonl` : modes **verite**, **action**, **envie** (Envies communes) et **defi** (Défi de la semaine).
- Une entrée JSON par ligne, UTF-8, sans virgule finale. Vérification : `node games/validate.mjs games/src/<fichier>.jsonl`.

## Catégories (et intensités permises)

| clé | préfixe id | thème | intensités |
|---|---|---|---|
| souvenirs | sou | souvenirs communs et passé de chacun (enfance, rencontre, premières fois tendres, voyages vécus) | 1, 2 |
| nous_deux | ndx | votre couple, vos habitudes, comment on se voit l'un l'autre, petites manies | 1, 2 |
| futur | fut | projets, rêves, vieillir ensemble, maison, voyages à venir, ambitions | 1, 2 |
| valeurs | val | convictions, choix de vie, argent, famille, amitié, éthique, limites | 1, 2 |
| fun | fun | drôle et absurde : hypothèses folles, superpouvoirs, situations ridicules | 1, 2 |
| romantique | rom | tendresse, déclarations, gestes d'amour, rendez-vous, sensualité douce | 1, 2, 3 |
| ose | ose | flirt, séduction, désir, sensualité suggestive (🌶) | 1, 2, 3 |
| intime | int | sexualité du couple : envies, fantasmes, préférences, limites, plaisir (🌶🌶🌶) | 2, 3 |

**Intensité** (piments) : 1 = léger, tout public ou flirt très doux ; 2 = coquin, sous-entendus assumés ;
3 = brûlant, direct sur le désir et la sexualité. Les catégories hors romantique/ose/intime sont surtout en 1,
avec environ 15 % en 2 (une pointe de flirt ou une question plus profonde). Romantique : ~50 % 1, ~40 % 2, ~10 % 3.
Osé : ~30 % 1, ~50 % 2, ~20 % 3. Intime : ~30 % 2, ~70 % 3.

**Limites (osé, intime)** : suggestif et direct mais en texte, sans description graphique d'actes, sans
vocabulaire cru ni pornographique. Toujours consenti, respectueux, centré sur le couple. Rien sur des tiers
réels, aucune violence, aucune contrainte, aucune substance. Les limites et le droit de dire non sont valorisés.

## Modes et formats

### devine (partie a)
La personne qui répond parle **d'elle-même** ; l'autre doit deviner sa réponse. Question à la 2ᵉ personne
(« tu »), sur ses goûts, son passé, ses réactions, ses pensées sur le couple. Pas de `{autre}`.
- `format: "choice"` : 2 à 4 `options` (≤ 50 car., variées, plausibles, aucune « bonne » réponse évidente).
- `format: "scale"` : échelle 1 à 10 avec `min` et `max` (≤ 28 car.), ex. `"min":"Pas du tout","max":"Complètement"`.
- `format: "text"` : réponse libre courte (un mot, un lieu, un plat, une phrase).
Répartition visée : ~45 % choice, ~25 % scale, ~30 % text.

### preferes (partie a)
Dilemme A/B : `text` (souvent « Tu préfères… ») et exactement 2 `options` (≤ 70 car.) équilibrées. Pas de `{autre}`.

### verite (partie b)
Question à laquelle on répond honnêtement, à voix haute ou par écrit, adressée à la 2ᵉ personne.
Peut parler de l'autre avec `{autre}` (remplacé par son prénom).

### action (partie b)
Petit gage à faire maintenant, à l'impératif, en quelques minutes, à la maison. Utilise `{autre}` pour désigner
l'autre. Ajoute `"remote": true` si l'action est faisable à distance (message, vocal, appel, photo envoyée
dans votre messagerie habituelle…) ; vise ~35 % d'actions à distance.

### envie (partie b)
Une envie à cocher oui / non / peut-être : courte phrase à l'infinitif ou nominale, sans point d'interrogation
(« Partir un week-end sans téléphone », « Un massage à l'huile chaude un dimanche soir »).
Activités, sorties, projets, et envies intimes selon la catégorie.

### defi (partie b)
Défi commun pour la semaine, à réaliser à deux ou l'un pour l'autre, commençant par « Cette semaine, … »
ou à l'impératif pluriel. Concret, faisable en 7 jours.

## Langue et neutralité de genre (obligatoire)

- Français naturel, oral, chaleureux. Tutoiement. Phrases courtes (8 à 170 caractères).
- **Aucun accord genré** pour « tu » ou l'autre : pas de « prêt / prête », « jaloux », « heureux », « (e) »,
  point médian, il / elle, le / la devant un verbe (« embrasse-le »). Reformule : « Qu'est-ce qui déclenche ta jalousie ? »,
  « Donne un baiser à {autre} », « Dis-lui… » (« lui » est neutre), « ton envie », « ta personne préférée ».
- Mots interdits : mari, femme, homme, copain/copine, chéri(e), garçon/fille, petit(e) ami(e).
- « On », « nous », « notre couple », « l'autre », « {autre} » sont parfaits.
- Pas de doublons ni de quasi-doublons : varie les situations, les verbes, les objets, les angles.
- Pas de références datées à l'actualité ni de marques.

## Format exact

```
{"id":"sou-a-0001","category":"souvenirs","mode":"devine","format":"choice","intensity":1,"text":"Quel détail de notre premier rendez-vous te revient en premier ?","options":["Le lieu","Ce que je portais","Notre fou rire","Le moment de se dire au revoir"]}
{"id":"sou-a-0002","category":"souvenirs","mode":"devine","format":"scale","intensity":1,"text":"À quel point étais-tu stressé par notre premier rendez-vous ?","min":"Zen total","max":"Mains moites"}
```
(Attention : « stressé » ci-dessus est genré → écrire « À quel point le stress était-il là avant notre premier rendez-vous ? ».)
```
{"id":"sou-a-0003","category":"souvenirs","mode":"devine","format":"text","intensity":1,"text":"Quel plat te rappelle ton enfance d'un seul coup ?"}
{"id":"sou-a-0750","category":"souvenirs","mode":"preferes","intensity":1,"text":"Tu préfères revivre…","options":["Notre premier baiser","Notre premier voyage"]}
{"id":"sou-b-0001","category":"souvenirs","mode":"verite","intensity":1,"text":"Quel moment avec {autre} aimerais-tu revivre exactement pareil ?"}
{"id":"sou-b-0400","category":"souvenirs","mode":"action","intensity":1,"text":"Mime à {autre} notre premier rendez-vous en 30 secondes, sans parler.","remote":false}
{"id":"sou-b-0700","category":"souvenirs","mode":"envie","intensity":1,"text":"Retourner ensemble sur le lieu de notre rencontre"}
{"id":"sou-b-0900","category":"souvenirs","mode":"defi","intensity":1,"text":"Cette semaine, recréez une photo de vos débuts, même pose, même sourire."}
```
Les identifiants se suivent sans trou dans chaque fichier (0001, 0002, …), tous modes confondus.
