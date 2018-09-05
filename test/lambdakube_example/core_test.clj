(ns lambdakube-example.core-test
  (require [clojure.test :refer :all]
           [lambdakube-example.core :as lke]
           [lambdakube.core :as lk]
           [lambdakube.util :as lku]
           [lambdakube.testing :as lkt]))


(defn test-module [$]
  (-> $
      ;; Tests that the master-slave configuration works. We set a
      ;; value in the master, and then read it from the slave,
      ;; expecting it to be the same.
      (lkt/test :redis-slave-configured
                {:num-be-slaves 1}
                [:backend-master :backend-slave]
                (fn [master slave]
                  (-> (lk/pod :test {})
                      ;; Wait for both the master and slave to be up
                      (lku/wait-for-service-port master :redis)
                      (lku/wait-for-service-port slave :redis)
                      ;; We use midje for the tests
                      (lku/add-midje-container
                       :test
                       '[[org.clojure/clojure "1.8.0"]
                         ;; Carmine is a Redis client library for Clojure
                         [com.taoensso/carmine "2.18.1"]]
                       ;; We pass the connection details for the Redis
                       ;; master and slave, as provided by our
                       ;; dependencies as constants to the test.
                       {:master-conn {:pool {} :spec {:host (:hostname master)
                                                      :port (-> master :ports :redis)}}
                        :slave-conn {:pool {} :spec {:host (:hostname slave)
                                                     :port (-> slave :ports :redis)}}}
                       '[(ns main-test
                           (:require [midje.sweet :refer :all]
                                     [taoensso.carmine :as car]))
                         (fact
                          ;; Set the value in the master.
                          (car/wcar master-conn
                                    (car/with-replies
                                      (car/set "foo" "bar")) => "OK")
                          ;; Wait for value to propagate to slave
                          (Thread/sleep 1000)
                          ;; Get the value from the slave.
                          (car/wcar slave-conn
                                    (car/with-replies
                                      (car/get "foo")) => "bar"))]))))
      ;; This tests the PHP code in the frontent. It sets value to a
      ;; key and then queries it.
      (lkt/test :frontend-set-and-get
                {:num-be-slaves 1
                 :num-fe-replicas 1}
                [:frontend]
                (fn [frontend]
                  (-> (lk/pod :test {})
                      ;; We wait for the frontend to come up
                      (lku/wait-for-service-port frontend :web)
                      (lku/add-midje-container
                       :test
                       '[[org.clojure/clojure "1.8.0"]
                         ;; We use clj-http to query the PHP page.
                         [clj-http "3.9.1"]]
                       ;; We pass the `base-url` to the PHP page as a
                       ;; constant. Values are based on the `frontend`
                       ;; dependency.
                       {:base-url (str "http://"
                                       (:hostname frontend)
                                       ":"
                                       (-> frontend :ports :web)
                                       "/guestbook.php")}
                       '[(ns main-test
                           (:require [midje.sweet :refer :all]
                                     [clj-http.client :as client]
                                     [clojure.string :as str]))
                         ;; A function that makes a query to the PHP
                         ;; page, by constructing a URL and making a
                         ;; GET request.
                         (defn wget [query]
                           (let [resp (client/get (str base-url "?" (str/join "&" (for [[k v] query]
                                                                                    (str k "=" v)))))]
                             (when-not (= (:status resp) 200)
                               (throw (Exception. (str "Bad status" (:status resp) ": " (:body resp)))))
                             (:body resp)))

                         ;; Set a value.
                         (fact
                          (wget {"cmd" "set"
                                 "key" "foo"
                                 "value" "bar"}) => "{\"message\": \"Updated\"}")
                         ;; Query that value.
                         (fact
                          (wget {"cmd" "get"
                                 "key" "foo"}) => "{\"data\": \"bar\"}")]))))))

(deftest kubetests
  (is (= (-> (lk/injector)
             (lke/module)
             (lk/standard-descs)
             (test-module)
             (lkt/kube-tests "lk-ex")) "")))


