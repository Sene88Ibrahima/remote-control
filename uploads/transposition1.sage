from sage.all import *

# Fonction principale
def transposition_cipher():
    # Initialisation du cryptosystème
    S = AlphabeticStrings()  # Alphabet de A à Z
    block_length = 14  # Taille de bloc
    T = TranspositionCryptosystem(S, Integer(block_length))  # Initialisation du cryptosystème

    # Génération d'une clé aléatoire
    K = T.random_key()
    K_inverse = T.inverse_key(K)

    print("=== Cryptosystème de transposition ===")
    print(f"Clé de chiffrement générée : {K}")
    print(f"Clé inverse pour le déchiffrement : {K_inverse}\n")

    # Demander à l'utilisateur son choix
    print("Que voulez-vous faire ?")
    print("1. Chiffrer un message")
    print("2. Déchiffrer un message")
    choice = input("Entrez votre choix (1 ou 2) : ")

    if choice not in ["1", "2"]:
        print("Choix invalide. Veuillez relancer le programme.")
        return

    # Demander le texte à traiter
    text = input("Entrez le texte : ").upper()

    # Ajouter des caractères de remplissage si nécessaire
    while len(text) % block_length != 0:
        text += "X"

    if choice == "1":
        # Chiffrement
        encipher = T(K)  # Fonction de chiffrement
        cipher_text = encipher(S(text))  # Chiffrement du texte
        print(f"Texte chiffré : {cipher_text}")
    elif choice == "2":
        # Déchiffrement
        decipher = T(K_inverse)  # Fonction de déchiffrement
        try:
            decoded_text = decipher(S(text))  # Déchiffrement du texte
            print(f"Texte déchiffré : {decoded_text}")
        except ValueError:
            print("Erreur : Le texte chiffré n'est pas valide pour cette clé.")

# Lancer le programme
if __name__ == "__main__":
    transposition_cipher()
