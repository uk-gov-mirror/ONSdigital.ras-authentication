#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

sudo apt-get -y update
sudo apt-get -y install docker.io

sudo docker run -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=changemeCHANGEME1234!' -p 1433:1433 -d microsoft/mssql-server-linux

sudo curl https://packages.microsoft.com/keys/microsoft.asc | sudo apt-key add -
sudo curl https://packages.microsoft.com/config/ubuntu/16.04/prod.list | sudo tee /etc/apt/sources.list.d/msprod.list
sudo apt-get update -o Dir::Etc::sourcelist="sources.list.d/msprod.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0"

sudo ACCEPT_EULA=y apt-get -y install mssql-tools
sudo /usr/bin/sqlcmd -S $HOST_IP -U SA -P 'changemeCHANGEME1234!' -i $DIR/ms-sql-server-db.sql
