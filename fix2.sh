set -e

VM="app/src/main/java/dev/garnetforge/app/viewmodel/MainViewModel.kt"
MAIN="app/src/main/java/dev/garnetforge/app/MainActivity.kt"

echo "---- inserting dashboardReady ----"

if ! grep -q "val dashboardReady" "$VM"; then
python3 - <<PY
import re
p="$VM"
s=open(p).read()

s=re.sub(
r'(private val _dashboardReady\s*=\s*MutableStateFlow\(false\))',
r'\1\n    val dashboardReady = _dashboardReady.asStateFlow()',
s
)

open(p,"w").write(s)
PY
fi

echo "---- fixing duplicate composable ----"

python3 - <<PY
p="$MAIN"
lines=open(p).read().splitlines()
out=[]
prev=False
for l in lines:
    if l.strip()=="@Composable":
        if prev: continue
        prev=True
    else:
        prev=False
    out.append(l)

open(p,"w").write("\n".join(out))
PY

echo
echo "==== VERIFY MainViewModel ===="
grep -n "dashboardReady" "$VM"

echo
echo "==== VERIFY duplicate composable removed ===="
grep -n "@Composable" "$MAIN" | head -n 10

echo
echo "Patch complete (no build run)"
