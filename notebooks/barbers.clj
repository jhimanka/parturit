^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns barbers
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [clj-http.client :as client]
            [jsonista.core :as json]))

;; # Hairdresser density in Finnish municipalities

;; During my summer holiday I visited the town of Saarijärvi in Finland and was struck
;; by a cluster of hairdressers seemingly large for such a small town. I decided to find out, using the open data
;; of Statistics Finland, whether Saarijärvi indeed had more hairdressers per capita compared
;; to other municipalities in Finland.

;; First we define an atom that holds the population of each Finnish municipality. This is a global
;; data structure because I might compare the municipalities using also other criteria and this way only
;; need to fetch the population numbers once.

(def populations (atom {}))

(defn get-populations []
  (let [apiurl "https://pxdata.stat.fi:443/PXWeb/api/v1/fi/Kuntien_avainluvut/2021/kuntien_avainluvut_2021_viimeisin.px"
        api-input {:query [{:code "Alue 2021"
                            :selection {:filter "all"
                                        :values ["*"]}}
                           {:code "Tiedot"
                            :selection {:filter "item"
                                        :values ["M411"]}}]
                   :response {:format "json-stat2"}}
        result (client/post apiurl {:body (json/write-value-as-string api-input)})
        result-edn (json/read-value  (:body result) json/keyword-keys-object-mapper)
        indexes (get-in result-edn [:dimension (keyword "Alue 2021") :category :index])
        labels (get-in result-edn [:dimension (keyword "Alue 2021") :category :label])
        value (:value result-edn)]
    (doseq [municipality labels]
      (swap! populations assoc (val municipality) (get value (get indexes (key municipality)))))))

(get-populations)

;; Then we fetch the number of hairdressers per municipality

(defn get-barbers-kunnat []
  (let [apiurl "https://pxdata.stat.fi:443/PXWeb/api/v1/fi/Toimipaikkalaskuri/Toimipaikkalaskuri/tmp_lkm_kunta.px"
        api-input {:query [{:code "Kunta"
                            :selection {:filter "all"
                                        :values ["*"]}}
                           {:code "Toimiala2008"
                            :selection {:filter "item"
                                        :values ["96021"]}}
                           {:code "Henkilöstön suuruusluokka"
                            :selection {:filter "item"
                                        :values ["_19"]}}]
                   :response {:format "json-stat2"}}
        input2 (json/write-value-as-string api-input)
        result (client/post apiurl {:body input2})
        result-edn (json/read-value  (:body result) json/keyword-keys-object-mapper)
        indeksit (get-in result-edn [:dimension :Kunta :category :index])
        labels (get-in result-edn [:dimension :Kunta :category :label])
        value (:value result-edn)]
    (for [municipality labels
          :let [bettername (val municipality)
                barbers (get value (get indeksit (key municipality)) 0)
                population  (get @populations bettername 0)]
          :when (and (string? bettername) (pos-int? barbers) (pos-int? population))]
      {:municipality (str/trim bettername)
       :barbers barbers
       :population population
       :proportion (if (every? #(and (number? %) (pos? %))  [barbers population])
                (int (float (/ population barbers)))
                0)})))

(def barbers   (get-barbers-kunnat))

;; The top ten in hairdresser density 
(clerk/table (->> barbers
                  (sort-by :proportion)
                  (take 10)))

;; And the ten municipalities that are most deprived of haute coiffure
(clerk/table (->> barbers
                  (sort-by :proportion)
                  (take-last 10)))

;; Saarijärvi is not special at all!
(first (filter #(= "Saarijärvi" (:municipality %)) barbers))

;; Here's a map coloured according to the hairdresser density that shows us that in the west people tend to
;; take better care of their hair. White areas mean missing data. 

(clerk/vl {:width 600
           :height 1000
           :data {:values (slurp "datasets/kunnat2022.json")
                  :format {:type "topojson" :feature "kunnat2022"}}
           :transform [{:lookup "properties.nimi"
                        :from {:data  {:values barbers}
                               :key "municipality"
                               :fields ["proportion" "barbers" "population"]}}]
           :projection {:type "mercator" :center [25,65] :scale 2000}
           :mark {:type "geoshape"}
           :encoding {:tooltip [{:field "properties.nimi" :title "Municipality"}
                                {:field "barbers" :title "Barbers"}
                                {:field "population" :title "Population"}
                                {:field "proportion" :title "Proportion"}]
                      :color {:field "proportion"
                              :type "quantitative"}}})

