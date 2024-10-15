^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns parturit
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [clj-http.client :as client]
            [jsonista.core :as json]))

;; # Parturikampaamojen määrä Suomen kunnissa

;; Kävin kesälomalla Saarijärvellä, ja ihmettelin kun joka käänteessä tuli vastaan parturi-kampaamoja.
;; Päätin selvittää Tilastokeskuksen avoimen datan pohjalta onko Saarijärvellä tosiaan paljon
;; kampaamoja asukasta kohti, verrattuna muihin Suomen kuntiin.

;; Ensin määritellään atomi, johon haemme Suomen kuntien väkiluvut. Teen tästä globaalin tietorakenteen,
;; koska tulen ehkä tekemään muitakin kuntakohtaisia vertailuja, ja näin kunnat ja väkiluvut tarvitsee
;; hakea vain kerran.

(def vakiluvut (atom {}))

(defn get-vakiluvut []
  (let [apiurl "https://pxdata.stat.fi:443/PxWeb/api/v1/fi/Kuntien_avainluvut/uusin/kuntien_avainluvut_viimeisin.px"
        api-input {:query [{:code "Alue"
                            :selection {:filter "all"
                                        :values ["*"]}}
                           {:code "Tiedot"
                            :selection {:filter "item"
                                        :values ["M411"]}}]
                   :response {:format "json-stat2"}}
        result (client/post apiurl {:throw-entire-message? true :body (json/write-value-as-string api-input)})
        result-edn (json/read-value  (:body result) json/keyword-keys-object-mapper)
        indeksit (get-in result-edn [:dimension (keyword "Alue") :category :index])
        labels (get-in result-edn [:dimension (keyword "Alue") :category :label])
        value (:value result-edn)]
    (doseq [kunta labels]
      (swap! vakiluvut assoc (val kunta) (get value (get indeksit (key kunta)))))))

(get-vakiluvut)

;; Sitten haemme parturikampaamojen määrän kunnittain

(defn get-parturit-kunnat []
  (let [apiurl "https://pxdata.stat.fi:443/PxWeb/api/v1/fi/Toimipaikkalaskuri/Toimipaikkalaskuri/tplask_toimipaikkalaskuri_pxt_14if_fi.px"
        api-input {:query [{:code "Kunta"
                            :selection {:filter "all"
                                        :values ["*"]}}
                           {:code "Toimiala"
                            :selection {:filter "item"
                                        :values ["S96021"]}}
                           {:code "Henkilöstön suuruusluokka"
                            :selection {:filter "item"
                                        :values ["SSS"]}}]
                   :response {:format "json-stat2"}}
        input2 (json/write-value-as-string api-input)
        result (client/post apiurl {:body input2})
        result-edn (json/read-value  (:body result) json/keyword-keys-object-mapper)
        indeksit (get-in result-edn [:dimension :Kunta :category :index])
        labels (get-in result-edn [:dimension :Kunta :category :label])
        value (:value result-edn)]
    (for [kunta labels
          :let [bettername  (val kunta)
                parturit (get value (get indeksit (key kunta)) 0)
                vakiluku  (get @vakiluvut bettername 0)]
          :when (and (string? bettername) (pos-int? parturit) (pos-int? vakiluku))]
      {:kunta (str/trim bettername)
       :partureita parturit
       :vakiluku vakiluku
       :suhde (if (every? #(and (number? %) (pos? %))  [parturit vakiluku])
                (int (float (/ vakiluku parturit)))
                0)})))

(def parturit   (get-parturit-kunnat))

;; Kymmenen kärki parturikampaamojen määrässä kuntalaista kohti
(clerk/table (->> parturit
                  (sort-by :suhde)
                  (take 10)))

;; Ja 10 kuntaa joissa asiat ovat huonoiten
(clerk/table (->> parturit
                  (sort-by :suhde)
                  (take-last 10)))

;; Saarijärvi ei ole parturitiheyden suhteen ollenkaan erikoinen!
(first (filter #(= "Saarijärvi" (:kunta %)) parturit))

;; Suhdeluvun mukaan väritetty kuntakartta, josta näkee , että Länsi-Suomessa hiuksista huolehditaan paremmin.
;; Valkoiset alueet tarkoittavat puuttuvaa tietoa.

(clerk/vl {:width 600
           :height 1000
           :data {:values (slurp "datasets/kunnat2022.json")
                  :format {:type "topojson" :feature "kunnat2022"}}
           :transform [{:lookup "properties.nimi"
                        :from {:data  {:values parturit}
                               :key "kunta"
                               :fields ["suhde" "partureita" "vakiluku"]}}]
           :projection {:type "mercator" :center [25,65] :scale 2000}
           :mark {:type "geoshape"}
           :encoding {:tooltip [{:field "properties.nimi" :title "Kunta"}
                                {:field "partureita" :title "Parturiliikkeitä"}
                                {:field "vakiluku" :title "Väkiluku"}
                                {:field "suhde" :title "Suhdeluku"}]
                      :color {:field "suhde"
                              :type "quantitative"}}})
