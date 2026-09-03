package br.com.iterasys.corte;

/**
 * Demonstração em console do cálculo de material conjugado: Poron (1400mm) +
 * cola transfer (1220mm) para atender a demanda de uma peça.
 */
public class ExemploMaterialConjugado {

    public static void main(String[] args) {
        Material poron = new Material("Poron", 1400);
        Material colaTransfer = new Material("Cola transfer", 1220);
        MaterialConjugado poronComCola = new MaterialConjugado(poron, colaTransfer);

        imprimirPlano(poronComCola, new Peca("Gaxeta", 200, 100, 262));
        imprimirPlano(poronComCola, new Peca("Suporte", 600, 700, 5));
    }

    private static void imprimirPlano(MaterialConjugado material, Peca peca) {
        PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(material, peca);

        System.out.println("Peça: " + peca.getNome() + " " + peca.getLarguraMm() + "x"
                + peca.getComprimentoMm() + "mm, quantidade " + peca.getQuantidade());
        System.out.println("Placas cheias (" + plano.larguraPlacaCheiaMm() + "x"
                + plano.comprimentoPlacaCheiaMm() + "mm, sem sobra): " + plano.placasCheias());

        if (plano.temTiraComplementar()) {
            TiraComplementar tira = plano.tiraComplementar();
            System.out.println("Tira complementar: " + tira.larguraMm() + "x" + tira.comprimentoMm()
                    + "mm (" + tira.quantidadePecas() + " peças)");

            SobraMaterial sobra = plano.sobra();
            System.out.println("Sobra: " + sobra.material().getNome() + " " + sobra.larguraMm()
                    + "x" + sobra.comprimentoMm() + "mm");
        } else {
            System.out.println("Quantidade fechou exatamente nas placas cheias, sem sobra.");
        }
        System.out.println();
    }
}
