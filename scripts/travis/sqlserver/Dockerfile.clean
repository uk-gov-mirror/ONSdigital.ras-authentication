FROM ubuntu:16.04

#Build with
#sudo docker build --no-cache -t cfidentity/ubuntu-16.04-sqlserver .


WORKDIR ~/

RUN export DEBIAN_FRONTEND=noninteractive

# Install curl, sudo and apt utilities
RUN apt-get -y update
RUN apt-get -y install apt-transport-https
RUN apt-get -y install curl
RUN apt-get -y install sudo

ADD ms-sql-server-db.sql /root/
ADD start-mssql.sh /root/

RUN curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN curl https://packages.microsoft.com/config/ubuntu/16.04/mssql-server.list | tee /etc/apt/sources.list.d/mssql-server.list
RUN curl https://packages.microsoft.com/config/ubuntu/16.04/prod.list | tee /etc/apt/sources.list.d/msprod.list

RUN echo "Downloading repo 1"
RUN apt-get update -o Dir::Etc::sourcelist="sources.list.d/mssql-server.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0"

RUN echo "Downloading repo 2"
RUN apt-get update -o Dir::Etc::sourcelist="sources.list.d/msprod.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0"

RUN echo "Installing server"
RUN apt-get -y install mssql-server

RUN echo "Installing tools"
RUN ACCEPT_EULA=y apt-get -y install mssql-tools

RUN echo "Installation complete..."
EXPOSE 1433

ENTRYPOINT /root/start-mssql.sh
##################### INSTALLATION END #####################


