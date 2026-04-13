set -e

FILE_VM="app/src/main/java/dev/garnetforge/app/viewmodel/MainViewModel.kt"
FILE_MAIN="app/src/main/java/dev/garnetforge/app/MainActivity.kt"

echo "== Fixing dashboardReady exposure =="

# add dashboardReady if missing
grep -q "val dashboardReady" "$FILE_VM" || \
sed -i '/_dashboardReady = MutableStateFlow/a\
    val dashboardReady = _dashboardReady.asStateFlow()\
' "$FILE_VM"

echo "== Fixing duplicate @Composable =="

# remove duplicate composable annotations
awk '
/@Composable/{
    if(prev==1){next}
    prev=1
    print
    next
}
{prev=0; print}
' "$FILE_MAIN" > "$FILE_MAIN.tmp" && mv "$FILE_MAIN.tmp" "$FILE_MAIN"

echo "== Verifying fixes =="

grep -n "dashboardReady" "$FILE_VM"
grep -n "@Composable" "$FILE_MAIN" | head

echo "== Building to verify =="
./gradlew assembleRelease --no-daemon

echo "== Git commit =="

git add -A
git commit -m "Fix dashboardReady StateFlow + remove duplicate @Composable causing release build failure"

echo "== Pushing =="
git push

echo "DONE"
