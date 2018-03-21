
_TEST_HOME=./tmp
_ORIG_HOME=$HOME

setup() {
  source gradle-completion.bash
  mkdir -p $_TEST_HOME
  HOME=$_TEST_HOME
}

teardown() {
  rm -rf $_TEST_HOME
  HOME=$_ORIG_HOME
}

test__should_init_cache_home() {
  __gradle-init-cache-dir

  if [[ ! -d $_TEST_HOME/.gradle/completion ]]; then
    fail "Expected the home directory to be created."
  fi
}

test__should_set_build_file() {
  touch $_TEST_HOME/build.gradle
  project_root_dir=$_TEST_HOME

  __gradle-set-build-file

  assertEquals "$_TEST_HOME/build.gradle" "$gradle_build_file"
}

test__should_set_build_file_for_custom_name() {
  echo "rootProject.buildFileName = 'sample.gradle'" > $_TEST_HOME/settings.gradle
  project_root_dir=$_TEST_HOME

  __gradle-set-build-file

  assertEquals "$_TEST_HOME/sample.gradle" "$gradle_build_file"
}

test__should_set_build_file_for_custom_name_no_spaces() {
  echo "rootProject.buildFileName='sample.gradle'" > $_TEST_HOME/settings.gradle
  project_root_dir=$_TEST_HOME

  __gradle-set-build-file

  assertEquals "$_TEST_HOME/sample.gradle" "$gradle_build_file"
}
