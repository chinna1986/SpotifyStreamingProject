package de.pascal.SpotifyAPI.JSON;

/**
 * Created by pascalmatthiesen on 02.10.13.
 */
public class UserInfo {

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    private Integer id;

    public UserInfoResult getResult() {
        return result;
    }

    public void setResult(UserInfoResult result) {
        this.result = result;
    }

    private UserInfoResult result;


    public class UserInfoResult {

        private String ab_collection_union;
        private String ab_test_group;
        private String ads;
        private String app_developer;
        private String catalogue;
        private String country;
        private String head_files;
        private String head_files_url;
        private String lastfm_session;
        private String license_agreements;
        private String link_tutorial_completed;
        private String post_open_graph;
        private String preferred_locale;
        private String product;
        private String user;
        private String wanted_licenses;

        public String getAb_collection_union() {
            return ab_collection_union;
        }

        public String getAb_test_group() {
            return ab_test_group;
        }

        public String getAds() {
            return ads;
        }

        public String getApp_developer() {
            return app_developer;
        }

        public String getCatalogue() {
            return catalogue;
        }

        public String getCountry() {
            return country;
        }

        public String getHead_files() {
            return head_files;
        }

        public String getHead_files_url() {
            return head_files_url;
        }

        public String getLastfm_session() {
            return lastfm_session;
        }

        public String getLicense_agreements() {
            return license_agreements;
        }

        public String getLink_tutorial_completed() {
            return link_tutorial_completed;
        }

        public String getPost_open_graph() {
            return post_open_graph;
        }

        public String getPreferred_locale() {
            return preferred_locale;
        }

        public String getProduct() {
            return product;
        }

        public String getUser() {
            return user;
        }

        public String getWanted_licenses() {
            return wanted_licenses;
        }
    }
}

