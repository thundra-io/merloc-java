#!/bin/bash

LAYER_NAME="merloc-java"
REGIONS=( "ap-northeast-1" "ap-northeast-2" "ap-south-1" "ap-southeast-1" "ap-southeast-2" "ap-east-1" "ca-central-1" "eu-central-1" "eu-north-1" "eu-south-1" "eu-west-1" "eu-west-2" "eu-west-3" "sa-east-1" "us-east-1" "us-east-2" "us-west-1" "us-west-2" )
WORKSPACE_DIR="workspace"
STATEMENT_ID_BASE="$LAYER_NAME-$(($(date +%s)))"

mvn clean install

pushd merloc-aws-lambda-gatekeeper

function cleanup {
    rm -rf $WORKSPACE_DIR
}
trap cleanup EXIT

cleanup

mvn clean package -P aws-lambda-layer

mkdir -p $WORKSPACE_DIR/java/lib

cp target/merloc-aws-lambda-gatekeeper.jar $WORKSPACE_DIR/java/lib
cp scripts/merloc_wrapper $WORKSPACE_DIR/merloc_wrapper

pushd $WORKSPACE_DIR

chmod +x ./merloc_wrapper

zip -r "$LAYER_NAME.zip" .

for REGION in "${REGIONS[@]}"
do

    echo "Releasing '$LAYER_NAME' layer for region $REGION ..."

    PUBLISHED_LAYER_VERSION=$(aws lambda publish-layer-version \
        --layer-name $LAYER_NAME \
        --description "$LAYER_NAME AWS Lambda layer" \
        --compatible-runtimes "java8" "java8.al2" "java11" \
        --zip-file "fileb://./$LAYER_NAME.zip" \
        --license-info "MIT" \
        --region $REGION \
        --endpoint-url "https://lambda.$REGION.amazonaws.com" \
        --query 'Version')

    echo "Released '$LAYER_NAME' layer with version $PUBLISHED_LAYER_VERSION"

    # #################################################################################################################

    echo "Adding layer permission for '$LAYER_NAME' layer with version $PUBLISHED_LAYER_VERSION" \
         " to make it accessible by everyone ..."

    STATEMENT_ID="$STATEMENT_ID_BASE-$REGION"
    aws lambda add-layer-version-permission \
        --layer-name $LAYER_NAME \
        --version-number $PUBLISHED_LAYER_VERSION \
        --statement-id "$LAYER_NAME-$STATEMENT_ID" \
        --action lambda:GetLayerVersion \
        --principal '*' \
        --region $REGION \
        --endpoint-url "https://lambda.$REGION.amazonaws.com"

    echo "Added public access layer permission for '$LAYER_NAME' layer"

done

popd

cleanup

popd
