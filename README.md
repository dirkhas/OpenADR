# OpenADR  [![Build Status](https://travis-ci.org/avob/OpenADR.svg?branch=master)](https://travis-ci.org/avob/OpenADR)  [![codecov](https://codecov.io/gh/avob/OpenADR/branch/master/graph/badge.svg)](https://codecov.io/gh/avob/OpenADR)


OpenADR protocol java implementation: *https://www.openadr.org/*

This project aims to provide minimal VEN / VTN 2.0a / 2.0b skeleton implementations. Relevant projects has to be extended to implement specific business logic: data source integration for a VEN stack, aggregation mechanism for VTN stack etc...

Module | Description
------------- | ------------- 
OpenADRSecurity | OADR security framework (authentication, xmlSignature)
OpenADRModel20b | OADR 2.0b model java classes generated from XSDL definition file
OpenADRServerVEN20b | OADR 2.0b VEN skeleton implementation
OpenADRServerVTN20b | OADR 2.0b VTN skeleton implementation

## Build dependencies
- Backend build dependencies: Java 8 / Maven 3
- Frontend build dependencies: NodeJS 8.15.0 / NPM 6.4.1

## Devops dependencies

- Install [Ansible](https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html#installing-the-control-machine)
- Install [Vagrant] v1.6.0(https://www.vagrantup.com/) and [Vagrant ansible provisioner plugin](https://www.vagrantup.com/docs/provisioning/ansible.html)
- Install [Virtualbox] v5.2 (https://www.virtualbox.org/)

## Certificates

Tests certificates are required to build project:
```shell
	# generate certificates test suite
	# mandatory passphare for devops purpose: changeme
	./generate_test_cert.sh
```

This command will create several VTN / VEN certificates upon a self-signed generated authority. This authority has to be installed and trusted. An admin trusted client certificate is also generated and can be installed to to perform client authentication:

- Install self-signed vtn certificate in your browser: cert/vtn.oadr.com-rsa.crt
- Install x509 admin certificate in your browser: cert/admin.oadr.com.crt (optional, authentication can be performed using login/pass: admin/admin)

## Oadr Prototyping Workbench VM

### Architecture

<p align="center">
  <img src="https://github.com/avob/OpenADR/raw/master/oadr_workbench_integration_infra.png?raw=true" alt="Sublime's custom image"/>
</p>


### Configuration

- Add "192.168.33.2 vtn.oadr.com" to your local "/etc/hosts"

### Build apps and start VM
```shell
	# build project with external middlewares and UI frontend dependencies
	mvn clean package -P external,frontend -DskipTests=true

	# create and provision VM
	cd devops/vtn20b_postgres
	vagrant up
```

### Endpoints

- VTN Control Swagger UI: https://vtn.oadr.com:8181/testvtn/swagger-ui.html
- VTN Control UI: https://vtn.oadr.com:8181/testvtn/
- VTN RabbitMQ Management UI: http://192.168.33.2:15672
- VTN Openfire Management UI: http://192.168.33.2:9090
- Nodered Terminal: http://192.168.33.2:1880 
- Ven1 Nodered Dashboard: http://192.168.33.2:1880/ui/#!/0

## Oadr Prototyping Workbench VM DEV MODE

## Architecture

<p align="center">
  <img src="https://github.com/avob/OpenADR/raw/master/oadr_workbench_dev_infra.png?raw=true" alt="Sublime's custom image"/>
</p>


### Configuration

- Add "127.0.0.1 vtn.oadr.com" to your local "/etc/hosts"
- Add "192.168.33.2 ven1.oadr.com" to your local "/etc/hosts"
 
### Start VM
```shell
	# build vtn backend with in-memory middleware and start locally
	mvn clean package -DskipTests=true
	java -jar OpenADRServerVTN20b/target ......

	# build vtn frontend
	cd OpenADRServerVTN20b/frontend
	npm start

    # create and provision VM
	cd {root}/devops/nodered
	vagrant up
```

### Endpoints

- VTN Control UI: http://localhost:3000/
- VTN Control Swagger UI: https://vtn.oadr.com:8181/testvtn/swagger-ui.html
- Nodered Terminal: http://192.168.33.2:1880 
- Ven1 Nodered Dashboard: http://192.168.33.2:1880/ui/#!/0

## Links

- [OpenADR 2.0b Spec](https://cimug.ucaiug.org/Projects/CIM-OpenADR/Shared%20Documents/Source%20Documents/OpenADR%20Alliance/OpenADR_2_0b_Profile_Specification_v1.0.pdf)
- [DRProgram Guide v1.0](https://www.openadr.org/assets/openadr_drprogramguide_v1.0.pdf)

