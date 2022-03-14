package src.Models;

import oracle.jdbc.proxy.annotation.Pre;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;


public class Requete {

    private Connection cnt;

    /**
     * Constructeur permettant à l'utilisateur de se connecter
     * @param user Nom d'utilisateur
     * @param password Mot de passe de l'utilisateur
     * @throws SQLException
     */
    public Requete(String user, String password) throws SQLException {
        String url = "jdbc:oracle:thin:@charlemagne.iutnc.univ-lorraine.fr:1521:infodb";
        cnt = DriverManager.getConnection(url,user, password);
    }

    /**
     * La méthode détermine la liste des véhicules selon une catégorie et la disponibilité d'une période
     * @param categorie code de la catégorie
     * @param startDate Date de début de période
     * @param endDate Date de fin de période
     * @return Liste des véhicules disponibles
     * @throws SQLException
     */
    public ArrayList<String> afficherListeVehicule(String categorie, String startDate, String endDate) throws SQLException {
        // On cree 2 requetes, une pour trouve les vehicules existant pour une categorie donnée et une autre pour savoir si ses vehicules sont libres
        String vehiculesRequetes = "SELECT * FROM vehicule where code_categ = ?";

        String immatriculationRequete =  "SELECT * FROM calendrier where no_imm = ?";

        // On prepare l'execution des requetes
        PreparedStatement vehiculesStt = cnt.prepareStatement(vehiculesRequetes);
        PreparedStatement immatriculationStt = cnt.prepareStatement(immatriculationRequete);

        // On definit la categorie que l'on recherche
        vehiculesStt.setString(1, categorie);

        // On recupere toutes les dates existantes entre 2 dates
        ArrayList<Date> totalDates = (ArrayList<Date>) dateEntreJours(startDate, endDate);

        // Execution de la requete pour recupere les immatriculations disponibles
        ResultSet rs = vehiculesStt.executeQuery();
        ResultSet resImmatriculation = null;

        // Cette map stock toutes les dates disponibles d'un vehicules (immatriculation)
        HashMap<String, ArrayList<Date>> disponibilite = new HashMap<>();

        // On traite chaque vehicule recupérés
        while(rs.next()) {
            immatriculationStt.setString(1, rs.getString(1));
            resImmatriculation = immatriculationStt.executeQuery();

            // Pour chaque vehicule disponible
            while(resImmatriculation.next()){
                // On ajoute chaque date disponbile pour chaque vehicule
                if(resImmatriculation.getString(3) == null){
                    if(disponibilite.containsKey(resImmatriculation.getString(1))) {
                        disponibilite.get(resImmatriculation.getString(1)).add(resImmatriculation.getDate(2));
                    } else {
                        ArrayList<Date> dates = new ArrayList<>();
                        dates.add(resImmatriculation.getDate(2));
                        disponibilite.put(resImmatriculation.getString(1), dates);
                    }
                }
            }
        }

        // On créé finalement la liste des véhicules disponibles
        ArrayList<String> vehiculesDispo = new ArrayList<>();

        // Pour cela on vérifie on vérifie que chaque date est libre pour tout les véhicules
        for(String vehicule : disponibilite.keySet()){
            boolean estDisponible = true;
            for(Date dateBesoin : totalDates){
                if(!disponibilite.get(vehicule).contains(dateBesoin)) estDisponible = false;
            }

            if(estDisponible) vehiculesDispo.add(vehicule);
        }

        rs.close();
        immatriculationStt.close();
        resImmatriculation.close();
        vehiculesStt.close();
        vehiculesStt.close();

        return vehiculesDispo;
    }

    /**
     * La méthode exécute une mise à jour du calendrier des réservations pour une certaine période
     * @param estDisponible boolean indiquant la disponibilité
     * @param startDate Date de début du calendrier
     * @param endDate Date de fin du calendrier
     * @param immatriculation Numéro d'immatriculation du véhicule
     * @throws SQLException
     */
    public boolean miseAJourCalendrier(boolean estDisponible, String startDate, String endDate, String immatriculation) throws SQLException {

        String nvResultat = " ";
        if(estDisponible) nvResultat = null;
        else nvResultat = "'x'";

        Statement stmt = cnt.createStatement();

        ArrayList<Date> dates = (ArrayList<Date>) dateEntreJours(startDate, endDate);

        int nbLignes = 0;

        for(Date d : dates) {
            String requete = "UPDATE calendrier set paslibre = " + nvResultat + " where no_imm = '" + immatriculation + "' and datejour = to_date('" + d + "','YYYY-MM-DD')";
            System.out.println("Appel : " + requete);
            nbLignes += stmt.executeUpdate(requete);
        }
        stmt.close();

        return dates.size() == nbLignes;
    }

    /**
     * La méthode sélectionne les dates entre 2 dates données en paramètres
     * @param debut date de début de liste
     * @param fin date de fin de liste
     * @return liste des dates
     */
    public static List<Date> dateEntreJours(String debut, String fin){
        LocalDate start = LocalDate.parse(debut);
        LocalDate end = LocalDate.parse(fin);
        List<Date> totalDates = new ArrayList<>();
        while (!start.isAfter(end)) {
            totalDates.add(Date.valueOf(start));
            start = start.plusDays(1);
        }
        return totalDates;
    }

    /**
     * La méthode calcul le montant d'une location d'un véhicule
     * @param modele modèle de véhicule choisi
     * @param nb_jours nombre de jours souhaités pour la location
     * @return résultat du calcul en fonction des tarifs
     * @throws SQLException
     */
    public int calculMontant(String modele, int nb_jours) throws SQLException{
        int res = 0;

        //Création de la requete qui devra sélectionner le tarif quotidien et hebdomadaire selon un modèle de véhicule
        String tarifRequete = "select TARIF_JOUR, TARIF_HEBDO from TARIF inner join CATEGORIE C2 on TARIF.CODE_TARIF = C2.CODE_TARIF inner join VEHICULE V on C2.CODE_CATEG = V.CODE_CATEG\n" +
                "    where MODELE = ?";

        //Préparation de l'exécution de la 1ère requete
        PreparedStatement tarifStt = cnt.prepareStatement(tarifRequete);

        //Définition du modèle donné en paramètre
        tarifStt.setString(1, modele);

        //Exécution de la requête qui aura pour but de récupérer tarif_jour et tarif_hebdo à chaque ligne de la table
        ResultSet rsTarif = tarifStt.executeQuery();

        //Parcours de l'exécution et qui incrémentera à la variable res le montant du calcul selon le modèle et le nombre de jours
        while (rsTarif.next()){
            int nb_semaine = 0;
            int n1 = rsTarif.getInt("tarif_jour");
            int n2 = rsTarif.getInt("tarif_hebdo");
            if (nb_jours < 7){
                res = n1 * nb_jours;
            } else {
                int mod = nb_jours % 7;
                if (mod == 0) {
                    nb_semaine = nb_jours / 7;
                    res = n2 * nb_semaine;
                } else if (mod != 0){
                    nb_semaine = (nb_jours - mod) / 7;
                    int res_semaine = n2 * nb_semaine;
                    int res_jour = n1 * mod;
                    res = res_semaine + res_jour;
                }
            }
        }
        return res;
    }

    /**
     * La méthode affiche les agences possédant toutes les catégories de véhicules
     * @return Variable String contenant les agences en question
     * @throws SQLException
     */
    public String affichageAgence() throws SQLException{
        String res = "";
        /*Creation de 3 requetes
        * Une pour compter le total des catégories existantes dans la table catégorie
        * Une autre pour compter le nombre de catégories utilisées dans la table vehicule
        * et enfin une requete pour selectionner les agences ayant un nombre de catégories donné*/
        String categorieRequete = "select count(code_categ) from categorie";
        String VehiculeCategorieRequete = "select count(code_categ) from VEHICULE group by CODE_CATEG";
        String VehiculeRequete = "select CODE_AG from VEHICULE group by CODE_AG having count(distinct CODE_CATEG) = ?";

        //préparation de l'execution de la 1ère requete
        PreparedStatement categorieStt = cnt.prepareStatement(categorieRequete);
        //execution de la requete comptant le nombre de categorie dans la table categorie
        ResultSet rsCategorie = categorieStt.executeQuery();
        //stockage du nombre de categorie
        int nbCateg = 0;
        while (rsCategorie.next()){
            int n = rsCategorie.getInt("count(code_categ)");
            nbCateg = n;
        }

        //préparation de l'execution de la 2ème requete
        PreparedStatement VerifCategorieVehiculeStt = cnt.prepareStatement(VehiculeCategorieRequete);
        //éxecution de la requete comptant le nombre de categorie inséré dans la table véhicule
        ResultSet rsVerifCategorieVehicule = VerifCategorieVehiculeStt.executeQuery();
        //stockage du nombre de categorie dans la table vehicule
        int nbVerifCateg = 0;
        while (rsVerifCategorieVehicule.next()){
            nbVerifCateg += 1;
        }

        //préparation de l'execution de la 3ème requete
        PreparedStatement VerifCategorieStt = cnt.prepareStatement(VehiculeRequete);
        //On définit le nombre total de catégories
        VerifCategorieStt.setInt(1, nbCateg);
        //Execution de la requete qui a pour but de selectionner les agences selon le nombre de categories qu'elles possèdent
        ResultSet rsVerifCategorie = VerifCategorieStt.executeQuery();
        /*Vérification du nombre de catégorie
        * si le nombre de type de catégories dans véhicule est égal au nombre total de catégorie dans catégorie
        * alors on séléctionne les agences ayant ce nombre de catégories
        * sinon on retourne une chaine indiquant qu'aucune agence ne possède toutes les catégories*/
        if (nbVerifCateg == nbCateg) {
            while (rsVerifCategorie.next()) {
                String codeAgence = rsVerifCategorie.getString("code_ag");
                res = res + " " + codeAgence;
            }
        } else {
            res = "Aucune agence ne possede toutes les categories";
        }

        categorieStt.close();
        VerifCategorieStt.close();
        rsCategorie.close();
        rsVerifCategorie.close();

        return res;
    }

    /**
     * La méthode affiche les clients selon le nombre de modèles en leur possession
     * @return Une variable String contentant les clients en question
     * @throws SQLException
     */
    /*public String affichageClient() throws SQLException{

        String res = "";

        //Création d'une requete qui selectionne les noms, villes et codes postales des clients selon le nombre de modèles qu'ils possèdent
        String clientRequete = "select nom, ville, codpostal from CLIENT inner join dossier on dossier.CODE_CLI\n" +
                "= CLIENT.CODE_CLI inner join VEHICULE on DOSSIER.NO_IMM = VEHICULE.NO_IMM group by nom, ville, CODPOSTAL\n" +
                "having count(MODELE) = ?";

        //Préparation de l'execution de la requete
        PreparedStatement clientStt = cnt.prepareStatement(clientRequete);

        //Définition du nombre de modèles (à 2 dans l'énoncé)
        clientStt.setInt(1, 2);

        //Execution de la requete
        ResultSet rsc = clientStt.executeQuery();

        //Parcours de l'execution qui incrémente l'attribut res au nom, ville et code postal du client à chaque itération
        while (rsc.next()){

            String nom = rsc.getString("nom");
            String ville = rsc.getString("ville");
            String codePostal = rsc.getString("codpostal");
            String client = nom + " " + ville + " " + codePostal;
            res += client + "\n";
        }

        clientStt.close();
        rsc.close();

        return res;
    }*/

    public String affichageClient(String modele1, String modele2) throws SQLException {

        String res = "";

        //Création d'une requete qui selectionne les noms, villes et codes postales des clients selon le modèle de véhicule qu'ils possèdent
        String clientRequete = "select nom, ville, codpostal from CLIENT inner join dossier on dossier.CODE_CLI" +
                " = CLIENT.CODE_CLI inner join VEHICULE on DOSSIER.NO_IMM = VEHICULE.NO_IMM where MODELE = ?";

        //Préparation de 2 éxecutions de la meme requete
        PreparedStatement clientStt1 = cnt.prepareStatement(clientRequete);
        PreparedStatement clientStt2 = cnt.prepareStatement(clientRequete);

        //Définition des différents modèles indiqués en paramètre si ces modèles sont différents
        if(modele1 != modele2) {
            clientStt1.setString(1, modele1);
            clientStt2.setString(1, modele2);
        } else {
            res = "les modeles sont les memes";
            return res;
        }

        //Exectution des 2 requetes selon leur modèle de vehicule chacune
        ResultSet rs1 = clientStt1.executeQuery();
        ResultSet rs2 = clientStt2.executeQuery();

        String res1 = "";
        String res2 = "";
        Boolean bool = true;
        //Création de 2 listes contenant les valeurs séléctionnées de chaque execution
        ArrayList<String> listRS1 = new ArrayList<>();
        ArrayList<String> listRS2 = new ArrayList<>();

        //Parcours des 2 exectutions en 1 seule fois
        while(bool){
            //Parcours des 2 executions qui stocke chaque ligne parcouru dans leurs listes dédiées
            while (rs1.next()){
                String client1nom = rs1.getString("nom");
                String client1ville = rs1.getString("ville");
                String client1codpostal = rs1.getString("codpostal");
                res1 = client1nom + " " + client1ville + " " + client1codpostal;
                listRS1.add(res1);
            }
            while (rs2.next()) {
                String client2nom = rs2.getString("nom");
                String client2ville = rs2.getString("ville");
                String client2codpostal = rs2.getString("codpostal");
                res2 = client2nom + " " + client2ville + " " + client2codpostal;
                listRS2.add(res2);
            }
            bool = false;
        }

        /*Pour chaque element de la listRS1
        * on verifie si la listRS2 contient les éléments de la liste listRS1
        * si c'est le cas, alors la méthode retourne les éléments en question*/
        for (String o: listRS1) {
            if (listRS2.contains(o)){
                res += o + "\n";
            }
        }

        clientStt1.close();
        clientStt2.close();
        rs1.close();
        rs2.close();

        return res;
    }
}