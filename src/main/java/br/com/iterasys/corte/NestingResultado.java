package br.com.iterasys.corte;

/**
 * Resultado do encaixe (nesting) de uma peça em fileiras ao longo da largura de um material,
 * empilhadas ao longo do comprimento aberto do rolo. {@code pecaGirada} indica se a peça
 * precisa ser cortada rotacionada 90° (nunca espelhada) para chegar a este resultado.
 */
public record NestingResultado(int pecasPorFileira, int fileiras, double comprimentoNecessarioMm, boolean pecaGirada) {

    public int capacidadeTotal() {
        return pecasPorFileira * fileiras;
    }
}
