#!/usr/bin/env bash
# ==============================================================================
# DEMO: Simulera lokal OutOfMemoryError & skapa Heap Dump utan Kubernetes
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "======================================================================"
echo "🧠 DEMO: Framkalla OOM & generera heap dump lokalt (utan K8s)"
echo "======================================================================"

echo "1. Kompilerar projektet..."
mvn -q compile

echo ""
echo "2. Kör MemoryLeakSimulation med -Xmx32m och automatisk Heap Dump..."
echo "   Kommando: java -Xmx32m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./oom.hprof ..."
echo "----------------------------------------------------------------------"

# Tillåt kommandot att avslutas med felkod vid OOM utan att skriptet dör tidigt
set +e
java -Xmx32m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./oom.hprof \
     -cp target/classes \
     com.vault.memory.MemoryLeakSimulation
EXIT_CODE=$?
set -e

echo "----------------------------------------------------------------------"
if [ -f "./oom.hprof" ]; then
    FILE_SIZE=$(ls -lh ./oom.hprof | awk '{print $5}')
    echo "✅ Heap Dump genererades framgångsrikt: ./oom.hprof (Storlek: $FILE_SIZE)"
    echo "🔍 Du kan nu öppna denna fil i Eclipse Memory Analyzer (MAT), VisualVM eller IntelliJ!"
else
    echo "⚠️ Ingen oom.hprof hittades."
fi
echo "======================================================================"
