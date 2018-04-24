#!/bin/bash

lein with-profile test migratus migrate
lein test
