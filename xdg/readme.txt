# generate java pojos based on a database schema for mattermost

# based on https://github.com/xgp/sql2java



mkdir -p src/main/resources
wget -P src/main/resources https://raw.githubusercontent.com/xgp/sql2java/master/sql2java-test/src/main/resources/sql2java.properties

mvn sql2java:sql2java compile

mkdir pojo
gen=target/generated-sources/sql2java/mm/data; for ii in $(cd $gen; ls * | grep -v Manager); do cat $gen/$ii | grep "\(^\S\|private\)" | grep -v "\(import\|modified\|initialized\|isNew\|^$\)" > pojo/$ii; done

(
  cd pojo
  rm PUBLICDatabase.java 

  # later changes that were applied in git
  sed -i "s/private/public/g" *
  sed -i "s/ fileIds/ [] fileIds/g" Posts.java 
  sed -i -e "s/ Bool/ bool/g" -e "s/ Long/ long/g" -e "s/ Integer/ int/g" *
)


mkdir -p ../src/main/java/mm
mv pojo ../src/main/java/mm/data




