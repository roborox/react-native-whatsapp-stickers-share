#!/usr/bin/env bash
set -e
npm install --registry http://10.7.3.6:8083/repository/npm-all/
npm run build
