//1 - Pacote
package intro;

//2 - Referencia as bibliotecas

// 3 - Classe
public class Medidas {
    // 3.1 - Atributos - Caracteristicas

   // 3.2 - Métodos e funções
        public static void main(String[] args) {
            calcularAreaModoCompacto();
            calcularAreaModoExtenso();
        }


        public static void calcularAreaModoExtenso(){
            //Calculo de área - exemplo: o tamanho do tapete ou do piso
            System.out.println("Cálculo de Areas - modo Extenso");
            int largura;
            int comprimento;
            int resultado;

            largura = 5; //largura recebe
            comprimento = 6; //comprimento recebe 3

            resultado = largura * comprimento;
            System.out.println("Para a largura de "+largura+"m e o comprimento de "
                    +comprimento+"m a area é de "+resultado+"m²");

        }
        public static void calcularAreaModoCompacto(){
            //Calculo de area reduzido
            System.out.println("Cálculo de Areas - modo curto");
            int largura = 4;
            int comprimento = 3;
            System.out.println("Para a largura de "+largura+"m e o comprimento de "
                    +comprimento+"m a area é de "+largura*comprimento+"m²");


        }

}
