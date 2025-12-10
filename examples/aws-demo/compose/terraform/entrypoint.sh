#!/bin/bash

# remove flags
rm -rf failed success

terraform init && terraform apply -auto-approve

if [ $? -eq 1 ]; then
    echo -e "\nFailed to apply infrastructure!\n"
    # create flag to let docker-compose healthcheck know that finished with error
    touch failed
    exit 1
else
    echo -e "\nSuccessfully applied infrastructure!\n"
    # create flag to let docker-compose healthcheck know that finished successfully
    touch success
    exit 0
fi
