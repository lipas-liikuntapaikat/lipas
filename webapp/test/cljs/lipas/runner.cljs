(ns lipas.ui.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [lipas.ui.core-test]))

(doo-tests 'lipas.ui.core-test)
