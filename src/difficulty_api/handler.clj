(ns difficulty-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [difficulty-api.dispatch :as dispatch]
            [difficulty-api.torn-api :as api]))

(defn invalid-api-key-handler [^Exception e data request]
  (bad-request (json/encode {:error {:msg "Invalid API key" :api-key (:api-key data)}})))

(defn unknown-api-key-handler [^Exception e data request]
  (not-found (json/encode {:error {:msg "Unknown API key" :api-key (:api-key data)}})))

(defn default-exception-handler [^Exception e data request]
  (log/error (str "Unhandled exception: " (.toString e) (string/join "\n" (map str (.getStackTrace e))) data))
  (internal-server-error {:error "Unhandled server error. Please notify the developer."}))

(defn wrap-logging [handler]
  (fn [req]
    (log/info
     (let [w (java.io.StringWriter.)]
       (pprint req w)
       (when (:body req)
         (io/copy (:body req) w))
       (.toString w)))
    (handler req)))

(defn wrap-cors [handler]
  (fn [req]
    (let [response (handler req)]
      (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))))

(defn app [http-client db]
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "difficulty-api"
                   :description "API for estimating difficulty of attacking players in torn"}}}
    :exceptions {:handlers {:unknown-api-key unknown-api-key-handler
                            :invalid-api-key invalid-api-key-handler
                            :compojure.api.exception/default default-exception-handler}}}

   (context "/api" []
     :middleware [wrap-cors]

     (POST "/apikey" []
       :return {:result s/Bool}
       :query-params [api-key :- s/Str]
       :summary "adds api key to database"
       (ok (do (log/info (format "Adding API Key: %s" api-key))
               {:result (dispatch/add-api-key http-client db api-key)})))

     (POST "/difficulties" []
       :return {:result {s/Int s/Keyword}}
       :query-params [api-key :- s/Str]
       :body [body {:torn-ids [s/Int]}]
       :summary "returns a list of difficulties"
       (ok {:result (do (log/info (format "Getting difficulties for API key %s of IDs %s"
                                          api-key (string/join ", " (:torn-ids body))))
                        (future (dispatch/update-battle-stats-if-outdated http-client db api-key))
                        (dispatch/update-attacks-if-outdated http-client db api-key)
                        (dispatch/difficulties db api-key (:torn-ids body)))})))))

(defrecord App [http-client db]
  component/Lifecycle

  (start [component]
    (assoc component :app (app http-client db)))
  (stop [component]
    (assoc component :app nil)))

(defn new-app [{:keys [http-client db] :as config}]
  (map->App config))
