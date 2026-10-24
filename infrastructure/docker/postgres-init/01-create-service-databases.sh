#!/bin/bash
set -euo pipefail

for database in \
    "${CATALOG_DB_NAME}" \
    "${INVENTORY_DB_NAME}" \
    "${ORDER_DB_NAME}" \
    "${PAYMENT_DB_NAME}" \
    "${KEYCLOAK_DB_NAME}"; do
    createdb --username "${POSTGRES_USER}" --owner "${POSTGRES_USER}" "${database}"
done
