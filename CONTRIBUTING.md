# Guide de Contribution

## Règles de Base

1. **Branches**
   - `main` : Branche principale du projet
   - `develop` : Branche de développement
   - `feature/*` : Pour les nouvelles fonctionnalités
   - `bugfix/*` : Pour les corrections de bugs
   - `hotfix/*` : Pour les corrections urgentes

2. **Workflow**
   - Créer une nouvelle branche pour chaque fonctionnalité/correction
   - Faire des commits atomiques et descriptifs
   - Créer une Pull Request pour fusionner dans `develop`
   - Après validation, fusionner dans `main`

3. **Messages de Commit**
   - Format : `[TYPE] Description`
   - Types : FEAT, FIX, DOC, STYLE, REFACTOR, TEST, CHORE
   - Exemple : `[FEAT] Ajout de la fonctionnalité de transfert de fichiers`

4. **Code Style**
   - Suivre les conventions Java
   - Documenter les méthodes publiques
   - Ajouter des commentaires pour le code complexe
   - Tester le code avant de soumettre

## Processus de Contribution

1. **Création d'une Branche**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/nouvelle-fonctionnalite
   ```

2. **Développement**
   - Faire des commits réguliers
   - Garder la branche à jour avec develop
   ```bash
   git add .
   git commit -m "[FEAT] Description"
   git pull origin develop
   ```

3. **Soumission**
   - Créer une Pull Request sur GitHub
   - Décrire les changements
   - Attendre la validation

## Responsabilités

### Membre 1
- Gestion des connexions SSL/TLS
- Interface graphique du serveur
- Gestion des utilisateurs

### Membre 2
- Interface graphique du client
- Transfert de fichiers
- Gestion des commandes

### Membre 3
- Tests et débogage
- Documentation
- Optimisation des performances

## Contact
Pour toute question, contacter le responsable du projet. 