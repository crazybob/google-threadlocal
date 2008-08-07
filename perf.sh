# Usage ./perf.sh [number 1-8]

echo ==========================================
echo TLTest${1}
echo ==========================================

JAVA='java -Xms128m -Xmx128m -server'

echo
echo Sun\'s ThreadLocal:
echo
$JAVA -cp out/test TLTest${1}

echo
echo crazybob\'s ThreadLocal:
echo
$JAVA -Xbootclasspath/p:out/main -cp out/test TLTest${1}

echo
