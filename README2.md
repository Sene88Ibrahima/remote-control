# Application de Contrôle à Distance Sécurisée en Java

## Table des matières
1. [Vue d'ensemble](#vue-densemble)
2. [Architecture](#architecture)
3. [Fonctionnalités](#fonctionnalités)
4. [Sécurité](#sécurité)
5. [Installation](#installation)
6. [Configuration](#configuration)
7. [Utilisation](#utilisation)
8. [Développement](#développement)
9. [Dépannage](#dépannage)
10. [Documentation technique](#documentation-technique)

## Vue d'ensemble

Application client-serveur sécurisée permettant le contrôle à distance d'un ordinateur avec :
- Communication chiffrée SSL/TLS
- Interface graphique moderne
- Transfert de fichiers bidirectionnel
- Exécution sécurisée de commandes
- Support multi-clients

### Prérequis
- Java JDK 8 ou supérieur
- 512 Mo RAM minimum
- Port réseau disponible (par défaut : 12345)
- Système d'exploitation : Windows/Linux/MacOS

## Architecture

### Structure du projet
```
projet/
├── src/
│   ├── client/
│   │   ├── Client.java
│   │   ├── ClientGUI.java
│   │   └── ClientMain.java
│   ├── server/
│   │   ├── Server.java
│   │   ├── ClientHandler.java
│   │   ├── Authentication.java
│   │   ├── FileTransferHandler.java
│   │   ├── ServerGUI.java
│   │   └── Logger.java
│   └── common/
│       └── SSLConfig.java
├── keystore/
│   └── keystore.jks
└── config/
    └── config.ini
```

## Fonctionnalités

### 1. Communication Sécurisée
- Chiffrement SSL/TLS
- Authentification mutuelle client/serveur
- Protection contre les attaques MITM
- Keep-alive pour la stabilité des connexions

### 2. Interface Graphique
- Thèmes personnalisables (Clair/Sombre)
- Gestion intuitive des fichiers
- Historique des commandes
- Logs en temps réel
- Barre de progression pour les transferts

### 3. Gestion des Fichiers
- Upload/Download sécurisé
- Gestion des interruptions
- Reprise des transferts
- Vérification d'intégrité

### 4. Exécution des Commandes
- Liste blanche de commandes autorisées
- Isolation des processus
- Capture des sorties stdout/stderr
- Timeouts configurables

## Sécurité

### 1. Configuration SSL/TLS
```bash
# Génération du keystore
keytool -genkeypair \
    -alias serverkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 365 \
    -keystore keystore.jks \
    -storepass changeit \
    -keypass changeit \
    -dname "CN=localhost, OU=Dev, O=MyCompany, L=City, ST=State, C=FR"

# Vérification du certificat
keytool -list -v -keystore keystore.jks
```

### 2. Authentification
- Hashage des mots de passe (SHA-256 + sel)
- Limitation des tentatives (3 max)
- Blocage temporaire (5 minutes)
- Sessions chiffrées

### 3. Commandes Sécurisées
```java
// Liste des commandes autorisées
ALLOWED_COMMANDS = {
    "dir", "ls", "cd", "pwd",
    "echo", "type", "cat",
    "ver", "hostname", "ipconfig"
}

// Liste des commandes dangereuses
DANGEROUS_COMMANDS = {
    "rm", "del", "format",
    "shutdown", "reboot"
}
```

## Installation

### 1. Compilation
```bash
# Création des répertoires
mkdir -p target/classes

# Compilation des sources
javac -d target/classes \
    src/client/*.java \
    src/server/*.java \
    src/common/*.java

# Création du JAR (optionnel)
jar cvf remote-control.jar -C target/classes .
```

### 2. Configuration SSL
```bash
# Copie du keystore
cp keystore.jks target/
```

### 3. Configuration du serveur
```bash
# Édition du fichier de configuration
echo "port=12345" > config.ini
echo "max_clients=10" >> config.ini
echo "timeout=300000" >> config.ini
```

## Utilisation

### 1. Démarrage du serveur
```bash
# Mode console
java -cp target/classes server.ServerMain

# Mode GUI
java -cp target/classes server.ServerMain --gui
```

### 2. Démarrage du client
```bash
java -cp target/classes client.ClientMain
```

### 3. Commandes disponibles
```bash
# Navigation
cd <directory>    # Changer de répertoire
pwd              # Afficher le répertoire courant
ls/dir          # Lister les fichiers

# Fichiers
upload <file>    # Envoyer un fichier
download <file>  # Télécharger un fichier
type <file>     # Afficher le contenu

# Système
ver             # Version du système
hostname        # Nom de l'hôte
ipconfig        # Configuration réseau
```

## Développement

### 1. Gestion des sockets
```java
// Serveur SSL
SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
serverSocket.setNeedClientAuth(true);

// Client SSL
SSLSocket clientSocket = (SSLSocket) ssf.createSocket(host, port);
clientSocket.startHandshake();
```

### 2. Transfert de fichiers
```java
// Envoi
byte[] buffer = new byte[8192];
while ((bytesRead = fis.read(buffer)) != -1) {
    os.write(buffer, 0, bytesRead);
    totalSent += bytesRead;
}

// Réception
while (totalRead < fileSize) {
    bytesRead = is.read(buffer, 0, Math.min(buffer.length, remaining));
    fos.write(buffer, 0, bytesRead);
    totalRead += bytesRead;
}
```

### 3. Multi-threading
```java
// Gestion des clients
Thread clientThread = new Thread(new ClientHandler(clientSocket));
clientThread.start();

// Keep-alive
Thread keepAliveThread = new Thread(() -> {
    while (isRunning) {
        sendKeepAlive();
        Thread.sleep(KEEP_ALIVE_INTERVAL);
    }
});
```

## Dépannage

### 1. Erreurs SSL
```
javax.net.ssl.SSLHandshakeException
- Vérifier le keystore
- Contrôler les permissions
- Vérifier la validité du certificat
```

### 2. Erreurs de connexion
```
java.net.ConnectException
- Vérifier le pare-feu
- Contrôler le port
- Vérifier le service
```

### 3. Erreurs de transfert
```
java.io.IOException
- Vérifier les timeouts
- Contrôler l'espace disque
- Vérifier les permissions
```

## Documentation technique

### 1. Protocole de communication
```
CLIENT -> SERVER : COMMAND <command>
SERVER -> CLIENT : RESPONSE <output>
CLIENT -> SERVER : UPLOAD <filename> <size>
SERVER -> CLIENT : READY
CLIENT -> SERVER : <file_data>
```

### 2. Métriques de performance
- Taille de buffer : 8192 octets
- Timeout par défaut : 60 secondes
- Keep-alive : 60 secondes
- Limite de connexions : 10 clients

### 3. Sécurité
- Algorithme de hashage : SHA-256
- Taille du sel : 16 octets
- Protocole SSL : TLS 1.2+
- Taille de clé RSA : 2048 bits

## Maintenance

### 1. Logs
```bash
# Format des logs
[TIMESTAMP] [LEVEL] [CLIENT] Message

# Niveaux de log
INFO    : Informations générales
WARNING : Avertissements
ERROR   : Erreurs critiques
DEBUG   : Informations de débogage
```

### 2. Sauvegarde
- Keystore : Quotidienne
- Logs : Rotation hebdomadaire
- Configuration : Versionné

### 3. Surveillance
- Connexions actives
- Utilisation mémoire
- Transferts en cours
- Tentatives d'intrusion

## Licence
Ce projet est sous licence MIT. Voir le fichier LICENSE pour plus de détails. 