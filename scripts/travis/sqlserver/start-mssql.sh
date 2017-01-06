#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Start SQL Server and set the base password"
sudo SA_PASSWORD=changemeCHANGEME1234! /opt/mssql/bin/sqlservr-setup --accept-eula --set-sa-password --start-service


echo "Create the uaa database and root user"
sudo sqlcmd -S localhost -U SA -P 'changemeCHANGEME1234!' -i $DIR/ms-sql-server-db.sql
