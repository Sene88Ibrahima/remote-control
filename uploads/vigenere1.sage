from sage.all import *

# Créer un cryptosystème Vigenère avec un alphabet standard et une clé de longueur 14
S = AlphabeticStrings()  # Alphabet standard (A-Z)
V = VigenereCryptosystem(S, 14)

def nettoyer_texte(texte):
    """
    Nettoie le texte en le convertissant en majuscules et en supprimant les caractères non alphabétiques.
    """
    return ''.join(char.upper() for char in texte if char.isalpha())

# Demander à l'utilisateur s'il veut chiffrer ou déchiffrer
action = input("Souhaitez-vous chiffrer ou déchiffrer un texte ? (c/d) ").strip().lower()

if action not in ('c', 'd'):
    print("Action non reconnue. Veuillez entrer 'c' pour chiffrer ou 'd' pour déchiffrer.")
    exit()

# Demander le texte à traiter
texte = input("Entrez le texte à traiter : ").strip()
texte = nettoyer_texte(texte)

if not texte:
    print("Le texte ne doit pas être vide ou contenir uniquement des caractères non alphabétiques.")
    exit()

if action == "c":
    # Générer une clé aléatoire
    K = V.random_key()
    print(f"Clé aléatoire générée : {K}")

    # Convertir le texte en élément du monoïde de chaînes
    M = S(texte)

    # Chiffrer le texte
    texte_chiffre = V.enciphering(K, M)
    print(f"Texte chiffré : {texte_chiffre}")

elif action == "d":
    # Demander la clé à l'utilisateur pour déchiffrer
    key_input = input("Entrez la clé utilisée pour le chiffrement : ").strip()
    key_input = nettoyer_texte(key_input)

    if not key_input:
        print("La clé ne doit pas être vide ou contenir uniquement des caractères non alphabétiques.")
        exit()

    K = S(key_input)

    # Convertir le texte en élément du monoïde de chaînes
    M = S(texte)

    # Déchiffrer le texte
    texte_dechiffre = V.deciphering(K, M)
    print(f"Texte déchiffré : {texte_dechiffre}")
