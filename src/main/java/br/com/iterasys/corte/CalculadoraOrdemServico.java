package br.com.iterasys.corte;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula o plano de corte de cada item de uma ordem de serviço e agrega o
 * consumo total de material e as sobras geradas por toda a ordem.
 */
public final class CalculadoraOrdemServico {

    private CalculadoraOrdemServico() {
    }

    public static ResumoOrdemServico calcular(OrdemServico ordem) {
        List<ResultadoItem> resultados = new ArrayList<>();
        Map<String, Double> consumoTotalPorMaterial = new LinkedHashMap<>();
        List<SobraMaterial> sobras = new ArrayList<>();

        for (ItemOrdemServico item : ordem.getItens()) {
            if (item.isMaterialConjugado()) {
                MaterialConjugado material = item.getMaterialConjugado();
                PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(material, item.getPeca());
                resultados.add(new ResultadoItem(item, null, plano));

                acumular(consumoTotalPorMaterial, material.getMaterialMaisLargo().getNome(), plano.consumoMaterialMaisLargoMm());
                acumular(consumoTotalPorMaterial, material.getMaterialMaisEstreito().getNome(), plano.consumoMaterialMaisEstreitoMm());

                if (plano.temTiraComplementar()) {
                    sobras.add(plano.sobra());
                }
            } else {
                Material material = item.getMaterialUnico();
                NestingResultado nesting = CalculadoraNesting.calcularAberturaNecessaria(material.getLarguraMm(), item.getPeca());
                resultados.add(new ResultadoItem(item, nesting, null));

                acumular(consumoTotalPorMaterial, material.getNome(), nesting.comprimentoNecessarioMm());
            }
        }

        return new ResumoOrdemServico(resultados, consumoTotalPorMaterial, sobras);
    }

    private static void acumular(Map<String, Double> totais, String nomeMaterial, double comprimentoMm) {
        totais.merge(nomeMaterial, comprimentoMm, Double::sum);
    }
}
