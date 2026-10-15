package br.com.conde.confiavel;

/**
 * O SORTEIO DA CASA, escrito aqui em vez de usar o da biblioteca.
 *
 * <p>E um gerador congruente linear com as constantes de Knuth. A razao de ser proprio e
 * medida: o teste roda com semente fixa em tres sistemas operacionais e precisa dar o mesmo
 * numero nos tres, e a implementacao da biblioteca nao e contrato entre versoes.
 *
 * <h2>O deslocamento de 32</h2>
 *
 * <p>O estado tem 64 bits e o que sai sao os 32 DE CIMA. Nos bits de baixo, os k ultimos
 * repetem com periodo 2 elevado a k, e a sacudida do canal, que e {@code ate(4)}, usaria
 * exatamente os dois ultimos.
 *
 * <p>O efeito seria o pior possivel para este repositorio: a sacudida viraria 0, 1, 2, 3, 0,
 * 1, 2, 3, para sempre. O canal desordenaria os quadros de um jeito so, sempre o mesmo, e a
 * repeticao seletiva, que existe justamente para lidar com chegada fora de ordem, seria
 * testada contra um unico padrao disfarcado de milhares. Ha um teste so sobre isso.
 */
public final class Sorteio {

    private long estado;

    public Sorteio(long semente) {
        this.estado = semente;
    }

    /** Um inteiro qualquer, dos 32 bits de cima do estado. */
    public int proximo() {
        estado = estado * 6364136223846793005L + 1442695040888963407L;

        return (int) (estado >>> 32);
    }

    /** Um valor de 0 ate quantos menos um. */
    public int ate(int quantos) {
        return Math.floorMod(proximo(), quantos);
    }

    /** Um numero entre 0 inclusive e 1 exclusive. */
    public double fracao() {
        return (proximo() & 0x7fffffff) / (double) 0x80000000L;
    }

    /** Verdadeiro com esta probabilidade. */
    public boolean com(double probabilidade) {
        return fracao() < probabilidade;
    }

    /** Um fluxo de bytes qualquer, que e o que vai ser entregue. */
    public byte[] fluxo(int tamanho) {
        byte[] saida = new byte[tamanho];

        for (int i = 0; i < tamanho; i++) saida[i] = (byte) ate(256);

        return saida;
    }
}
