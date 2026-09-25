# Fiche Chrome Web Store

## Nom
VOID-RAY — EnderrVPN

## Résumé (132 caractères max)
FR : Protège ta navigation avec ton abonnement EnderrVPN : proxy HTTPS chiffré, quota et expiration en un coup d'œil.
EN : Protect your browsing with your EnderrVPN subscription: encrypted HTTPS proxy, quota and expiry at a glance.

## Description
VOID-RAY est l'extension officielle des abonnés EnderrVPN.

• Connecte ton abonnement avec ton lien sub.enderr.win
• Un clic pour faire passer tes pages web par le proxy chiffré d'EnderrVPN
• Seul Chrome est concerné : les autres applications ne changent pas
• Consommation en direct : jauge, envoyé / reçu, consommation par jour
• Détails de l'abonnement : état, quota, expiration, dernière connexion
• IP publique, pays et latence vérifiés après la connexion
• Protection contre les fuites WebRTC
• Thème sombre, clair ou automatique, 6 couleurs d'accent, français et anglais

Un abonnement EnderrVPN est nécessaire.

## Catégorie
Outils / Confidentialité et sécurité

## Objectif unique
Faire passer la navigation Chrome par le proxy HTTPS d'EnderrVPN et afficher l'état de l'abonnement.

## Justification des permissions
- **proxy** : configurer Chrome pour utiliser le proxy EnderrVPN pendant la connexion, puis le retirer.
- **webRequest, webRequestAuthProvider** : répondre à la demande d'authentification du proxy avec les identifiants de l'abonné.
- **privacy** : bloquer les fuites d'IP par WebRTC pendant la connexion.
- **storage** : garder le lien d'abonnement, les préférences (thème, langue) et l'historique de consommation.
- **alarms** : actualiser l'abonnement toutes les 5 minutes.
- **Accès à tous les sites (host permissions)** : nécessaire pour authentifier le proxy sur toutes les pages visitées et lire l'abonnement sur sub.enderr.win.

## Utilisation des données
Aucune donnée n'est vendue ni transmise à des tiers. Le lien d'abonnement reste dans le navigateur
et n'est envoyé qu'à sub.enderr.win. Voir PRIVACY.md.
