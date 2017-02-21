rm -rf ./target
mvn clean package
GP2LVS_VERSION=$(cat pom.xml| grep version | head -n 1 | cut -d ">" -f 2 | cut -d "<" -f 1)
GP2LVS_FOLDER=gp2lvs-$GP2LVS_VERSION

mkdir -p ./target/$GP2LVS_FOLDER/lib/
mv ./target/lib/*.jar ./target/$GP2LVS_FOLDER/lib/
mv ./target/*.jar ./target/$GP2LVS_FOLDER/
mv ./target/run.bat ./target/$GP2LVS_FOLDER/
chmod +x ./target/run.sh
mv ./target/run.sh ./target/$GP2LVS_FOLDER/

cd target
zip -r ./$GP2LVS_FOLDER.zip ./$GP2LVS_FOLDER
cd ..
