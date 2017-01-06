FROM ubuntu:16.04

WORKDIR ~/

RUN export DEBIAN_FRONTEND=noninteractive

# Install curl, sudo and apt utilities
RUN apt-get -y update
RUN apt-get -y install apt-transport-https
RUN apt-get -y install curl
RUN apt-get -y install sudo

ADD ms-sql-server-db.sql /root/

RUN curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN curl https://packages.microsoft.com/config/ubuntu/16.04/mssql-server.list | tee /etc/apt/sources.list.d/mssql-server.list
RUN curl https://packages.microsoft.com/config/ubuntu/16.04/prod.list | tee /etc/apt/sources.list.d/msprod.list

RUN echo "Downloading repo 1"
RUN apt-get update -o Dir::Etc::sourcelist="sources.list.d/mssql-server.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0"

RUN echo "Downloading repo 2"
RUN apt-get update -o Dir::Etc::sourcelist="sources.list.d/msprod.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0"

RUN echo "Installing server"
RUN apt-get -y install mssql-server

RUN echo "Start SQL Server and set the base password"
RUN SA_PASSWORD=changemeCHANGEME1234! /opt/mssql/bin/sqlservr-setup --accept-eula --set-sa-password --start-service

RUN echo "Installing tools"
RUN ACCEPT_EULA=y apt-get -y install mssql-tools

RUN echo "Create the uaa database and root user"
RUN sqlcmd -S localhost -U SA -P 'changemeCHANGEME1234!' -i $DIR/ms-sql-server-db.sql

RUN echo "Installation complete...database listening on port 1433"
EXPOSE 1433
##################### INSTALLATION END #####################


