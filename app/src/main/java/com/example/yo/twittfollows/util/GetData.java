package com.example.yo.twittfollows.util;

import android.content.Context;
import android.util.Log;

import com.example.yo.twittfollows.bd.DataSource;
import com.example.yo.twittfollows.bd.MyDBHelper;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import twitter4j.IDs;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by Pablo on 11/01/2018.
 * Clase que ejecuta las querys pertinentes a la API REST de Twitter para
 * obtener las listas de usuarios necesarias en las distintas pestañas de
 * CentralActivity, así como los datos del usuario conectado necesarios
 * en su perfil.
 */

public class GetData {
    public static final int ERROR_NO_INTERNET = 1;
    public static final int ERROR_RATE_LIMIT_EXCEDED = 2;
    public static final int NO_ERROR = 0;
    // Singleton
    private static final GetData ourInstance = new GetData();
    //BBDD
    private static DataSource dataSource;
    /**
     * Lista de usuarios que siguen al User.
     */
    private List<TwitterUser> followersList;
    /**
     * Lista de usuarios a los que sigue el User.
     */
    private List<TwitterUser> followingList;
    /**
     * Lista desactualizada de los seguidores (recent unfollowers)
     */
    private List<TwitterUser> previousFollowersList;
    /**
     * Lista de users que siguen al User pero él no les sigue.
     */
    private List<TwitterUser> fansList;
    /**
     * Lista de usuarios que el User sigue pero no le siguen de vuelta.
     */
    private List<TwitterUser> mutualsList;
    /**
     * Lista de usuarios que se se siguen mutuamente con el User.
     */
    private List<TwitterUser> notFollowingYouList;
    private TwitterSession session;
    private Twitter twitter;

    /**
     * Constructor de la clase GetData.
     * Inicializa las listas de users,
     * obtiene la sesión activa de twitter e
     * inicializa el objeto twitter4j.Twitter usado para las querys a la API.
     */
    private GetData() {
        followersList = new ArrayList<>();
        followingList = new ArrayList<>();
        fansList = new ArrayList<>();
        mutualsList = new ArrayList<>();
        notFollowingYouList = new ArrayList<>();

        // Claves de autenticación para usar twitter4j
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey("2wM6G1zTaOvSjNoAZQaP9UbUr")
                .setOAuthConsumerSecret("4tlSpm7l1s8SHPSrmToPgDZPRJWPxhUonuZ2sTxJUnQJol3QBf")
                .setOAuthAccessToken("262623444-i1a20bjgLXj3lkW3XVq9S75HpoWAL4fw72CGXT6X")
                .setOAuthAccessTokenSecret("YrN4iQC8eKsym5D7xHvf25kwj4y3mMBJmFBTF4QCKU3P9");
        twitter = new TwitterFactory(cb.build()).getInstance();

    }

    public static GetData getInstance() {
        return ourInstance;
    }

    /**
     * Ejecuta una query contra la API de Twitter y obtiene los ids de los
     * usuarios que siguen al usuario que tiene la sesión activa. Luego hace
     * otra query para obtener los datos de estos usuarios.
     *
     * @return true si obtiene los datos, false si hay un error
     */
    private int fetchFollowers() {
        dataSource.open();
        previousFollowersList = new ArrayList<>(followersList);
        dataSource.getDbHelper().deleteFromTableFollowers(dataSource.getBBDD());
        followersList = new ArrayList<>();
        long[] ids;
        try {
            //Se obtienen los ids de los followers
            IDs idsResponse = twitter.getFollowersIDs(session.getUserName(), -1);
            ids = idsResponse.getIDs();
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get followers' ids: " + te.getMessage());
            return ERROR_RATE_LIMIT_EXCEDED;
        }
        Log.e("Ids followers", String.valueOf(ids.length));
        if (ids.length > 0) {
            try {
                int i = 0;
                int n = 0;
                int limit;
                do {
                    // Corta el array de ids en array de 100 como max
                    limit = (i + 99 > ids.length) ? ids.length : i + 99;
                    long[] idsSlice = Arrays.copyOfRange(ids, i, limit);
                    // Se obtienen los datos de los users para los ids
                    Log.e("Ids followers slice", String.valueOf(idsSlice.length));
                    Log.e("i", String.valueOf(i));
                    Log.e("limit", String.valueOf(limit));
                    ResponseList<User> users = twitter.lookupUsers(idsSlice);

                    for (User u : users) {
                        TwitterUser twitterUser = new TwitterUser(
                                u.getId(),
                                u.getScreenName(),
                                u.getName(),
                                u.getOriginalProfileImageURL()
                        );
                        dataSource.createTwitterUser(twitterUser, MyDBHelper.TABLE_FOLLOWERS, n++);
                        followersList.add(twitterUser);
                    }
                    i += 100;
                } while (limit != ids.length);

            } catch (TwitterException te) {
                te.printStackTrace();
                System.out.println("Failed to get followers info: " + te.getMessage());
                return GetData.ERROR_RATE_LIMIT_EXCEDED;
            }
            Log.e("Nº followers", String.valueOf(followersList.size()));
        }
        dataSource.close();
        return GetData.NO_ERROR;
    }

    private void fetchRecentUnfollowers() {
        if (previousFollowersList != null) {
            dataSource.open();
            dataSource.getDbHelper().deleteFromTableUnfollowers(dataSource.getBBDD());
            List<TwitterUser> followers = getFollowers();
            List<TwitterUser> previousFollowers = new ArrayList<>(previousFollowersList);
            previousFollowers.removeAll(followers);
            for (TwitterUser twitterUser : previousFollowers) {
                dataSource.createTwitterUser(twitterUser, MyDBHelper.TABLE_UNFOLLOWERS, 0);
            }
            dataSource.close();

        }

    }


    /**
     * Ejecuta una query contra la API de Twitter y obtiene los ids de los
     * users a los que sigue el usuario que tiene la sesión activa. Luego
     * ejecuta una segunda query que obtiene los datos de estos usuarios.
     *
     * @return true si obtiene los datos, false si hay un error
     */
    private int fetchFollowing() {
        dataSource.open();
        dataSource.getDbHelper().deleteFromTableFollowing(dataSource.getBBDD());
        followingList = new ArrayList<>();
        long[] ids = null;
        try {
            //Se obtienen los ids de los followers
            IDs idsResponse = twitter.getFriendsIDs(session.getUserName(), -1);
            ids = idsResponse.getIDs();
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get following' ids: " + te.getMessage());
            return ERROR_RATE_LIMIT_EXCEDED;
        }
        Log.e("Ids following", String.valueOf(ids.length));

        if (ids.length > 0) {
            try {
                int i = 0;
                int n = 0;
                int limit;
                do {
                    // Corta el array de ids en array de 100 como max
                    limit = (i + 99 > ids.length) ? ids.length : i + 99;
                    long[] idsSlice = Arrays.copyOfRange(ids, i, limit);
                    Log.e("Ids following slice", String.valueOf(idsSlice.length));
                    Log.e("i", String.valueOf(i));
                    Log.e("limit", String.valueOf(limit));

                    // Se obtienen los datos de los usuarios para los ids dados
                    ResponseList<User> users = twitter.lookupUsers(idsSlice);
                    for (User u : users) {
                        TwitterUser twitterUser = new TwitterUser(
                                u.getId(),
                                u.getScreenName(),
                                u.getName(),
                                u.getOriginalProfileImageURL()
                        );
                        dataSource.createTwitterUser(twitterUser, MyDBHelper.TABLE_FOLLOWING, n++);
                        followingList.add(twitterUser);
                    }
                    i += 100;
                } while (limit < ids.length);

            } catch (TwitterException te) {
                te.printStackTrace();
                System.out.println("Failed to get following info: " + te.getMessage());
                return GetData.ERROR_RATE_LIMIT_EXCEDED;
            }
            Log.e("Nº following", String.valueOf(followingList.size()));
        }
        dataSource.close();
        return GetData.NO_ERROR;
    }

    /**
     * Obtiene los datos para el usuario que tiene la sesión activa.
     *
     * @return User el usuario, o null si hay algún error
     */
    public User getCurrentUserData() {
        try {
            session = TwitterCore.getInstance().getSessionManager().getActiveSession();
            return twitter.showUser(session.getUserName());
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get user info: " + te.getMessage());
        }
        return null;
    }

    /**
     * Obtiene los datos de seguidores y seguidos para el usuario con la
     * sesión activa.
     * <p>
     * Los datos son obtenidos de la bd o de la API dependiendo del parámetro.
     *
     * @param db si es true obtiene los datos de la bd, si es false de la API
     * @return un int dependiendo de si hay algún error.
     */
    public int fetchData(boolean db, Context context) {
        dataSource = new DataSource(context);
        if (db) {
            session = TwitterCore.getInstance().getSessionManager().getActiveSession();
            return fetchFromDB();
        } else {
            session = TwitterCore.getInstance().getSessionManager().getActiveSession();
            if (!new Conectivity(context).hayConexion())
                return GetData.ERROR_NO_INTERNET;
            if (fetchFollowing() + fetchFollowers() == GetData.NO_ERROR) {
                fetchRecentUnfollowers();
                return GetData.NO_ERROR;
            } else
                return GetData.ERROR_RATE_LIMIT_EXCEDED;
        }
    }


    private int fetchFromDB() {
        dataSource.open();
        followersList = dataSource.getAllUsers(MyDBHelper.TABLE_FOLLOWERS);
        followingList = dataSource.getAllUsers(MyDBHelper.TABLE_FOLLOWING);
        dataSource.close();

        return GetData.NO_ERROR;
    }

    /**
     * Devuelve una copia de la lista de seguidores del usuario con la sesión
     * activa.
     *
     * @return lista de usuarios que siguen al User
     */
    public List<TwitterUser> getFollowers() {
        return new ArrayList<>(followersList);
    }

    /**
     * Devuelve una copia de la lista de seguidos del usuario con la sesión
     * activa.
     *
     * @return lista de usuarios seguidos por el User
     */
    public List<TwitterUser> getFollowing() {
        return new ArrayList<>(followingList);
    }

    public List<TwitterUser> getUnfollowers() {
        dataSource.open();
        List<TwitterUser> res = dataSource.getAllUsers(MyDBHelper.TABLE_UNFOLLOWERS);
        dataSource.close();
        return new ArrayList<>(res);

    }

    /**
     * Obtiene las listas de usuarios necesarias para las tabs.
     * <p>
     * -fansList: lista de users que siguen al User pero él no les sigue
     * -notFollowingYouList: lista de usuarios que el User sigue pero no
     * le siguen de vuelta
     * -mutualsList: lista de usuarios que se se siguen mutuamente con el User
     */
    public void calculateLists() {

        // Followers - Following
        Log.e("Followers antes", String.valueOf(getFollowers().size()));
        fansList = getFollowers();
        fansList.removeAll(getFollowing());
        Log.e("Fans list", String.valueOf(fansList.size()));
        Log.e("Followers despues", String.valueOf(getFollowers().size()));

        // Following - Followers
        notFollowingYouList = getFollowing();
        notFollowingYouList.removeAll(getFollowers());
        Log.e("NotF list", String.valueOf(notFollowingYouList.size()));

        //Followers ∪ Following
        mutualsList = getFollowing();
        mutualsList.retainAll(new HashSet<TwitterUser>(getFollowers()));
        Log.e("Mutuals list", String.valueOf(mutualsList.size()));
    }

    /**
     * Devuelve la lista de fans del User.
     *
     * @return lista de usuarios que son fans del User
     */
    public List<TwitterUser> getFansList() {
        return fansList;
    }

    /**
     * Devuelvela lista de seguidores mutuos del User.
     *
     * @return una lista de usuarios que son mutos con el User
     */
    public List<TwitterUser> getMutualsList() {
        return mutualsList;
    }

    /**
     * Devuelve la lista de usuarios a los que el User sigue
     * pero no le siguen de vuelta.
     *
     * @return lista de users que no siguen de vuelta al User
     */
    public List<TwitterUser> getNotFollowingYouList() {
        return notFollowingYouList;
    }

    public void clear() {
        fansList.clear();
        followingList.clear();
        mutualsList.clear();
        notFollowingYouList.clear();
        followersList.clear();
    }

}

