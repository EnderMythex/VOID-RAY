# VOID-RAY — extension Chrome

Extension Manifest V3 pour les abonnés EnderrVPN, avec le style de la page d'abonnement
(thème sombre / clair / auto, 6 couleurs d'accent, français / anglais).

- Au premier clic, elle demande le lien `https://sub.enderr.win/ender/…` (seuls ces liens
  sont acceptés, et l'abonnement est vérifié sur le serveur).
- Elle affiche le quota, la consommation (jauge + par jour), l'expiration, le compte…
- Le bouton ⏻ fait passer **uniquement les pages web de Chrome** par le proxy HTTPS
  d'EnderrVPN. Les autres applications du PC ne sont pas touchées.
- Pendant la connexion : badge « ON » sur l'icône, IP publique / pays / latence vérifiés,
  protection WebRTC (l'IP réelle ne fuit pas par WebRTC).

## Pourquoi un proxy HTTPS

Une extension Chrome ne peut pas exécuter Xray ni parler VLESS / Reality / WebSocket :
Chrome sait seulement utiliser un proxy HTTP(S) ou SOCKS. L'extension utilise donc une
entrée **HTTP + TLS** d'Xray (un « proxy HTTPS »), à ajouter une fois dans 3x-ui.

## Mise en place côté serveur (3x-ui)

1. **DNS** : crée `proxy.enderr.win` → IP du serveur, **sans** le proxy Cloudflare
   (nuage gris : Cloudflare ne relaie pas un proxy HTTPS).
2. **Certificat** TLS pour `proxy.enderr.win` (menu `x-ui` → SSL Certificate, ou certbot).
3. **3x-ui → Inbounds → Add Inbound**
   - Protocol : `http` · Port : `8443`
   - Accounts : un compte par abonné — **Username = identifiant d'abonnement**
     (la fin du lien, ex. `<identifiant>`) · **Password = UUID du client**
     (celui des configurations VLESS de cet abonné).
   - Security : `TLS`, domaine `proxy.enderr.win`, chemins du certificat et de la clé.
4. Si l'hôte ou le port sont différents, modifie `PROXIES` dans `src/config.js`
   (tu peux aussi y ajouter plusieurs serveurs : ils apparaissent dans la liste).

L'extension envoie automatiquement ces identifiants (identifiant d'abonnement + UUID
récupéré dans l'abonnement) quand le proxy les demande.

> Le trafic de l'entrée `http` n'est pas compté dans le quota de l'abonnement 3x-ui
> (les comptes du proxy HTTP ne sont pas liés aux clients).

## Tester en local

`chrome://extensions` → activer **Mode développeur** → **Charger l'extension non
empaquetée** → choisir ce dossier `extension/`.

## Publier sur le Chrome Web Store

1. Le workflow GitHub **Extension** produit `voidray-extension.zip` (artefact).
2. Sur https://chrome.google.com/webstore/devconsole (compte développeur, 5 $ une fois) :
   **Nouvel élément** → envoyer le zip.
3. Remplir la fiche avec `STORE.md` (description, justification des permissions) et
   indiquer l'adresse de la politique de confidentialité (`PRIVACY.md`, à publier en ligne).
4. Captures d'écran : 1280×800 ou 640×400.
